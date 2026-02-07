package com.rankweis.uppercut.karate.debugging;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.intellij.debugger.NoDataException;
import com.intellij.debugger.PositionManager;
import com.intellij.debugger.SourcePosition;
import com.intellij.debugger.engine.CompoundPositionManager;
import com.intellij.debugger.engine.DebugProcess;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.PositionManagerImpl.JavaSourcePosition;
import com.intellij.debugger.impl.DebuggerUtilsEx;
import com.intellij.debugger.requests.ClassPrepareRequestor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.psi.PsiElement;
import com.rankweis.uppercut.karate.psi.GherkinFileType;
import com.sun.jdi.AbsentInformationException;
import com.sun.jdi.Location;
import com.sun.jdi.ReferenceType;
import com.sun.jdi.VirtualMachine;
import com.sun.jdi.request.ClassPrepareRequest;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import one.util.streamex.StreamEx;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Managing the debugger cache positions.
 */
@SuppressWarnings("ALL")
@Slf4j
public class KaratePositionManager implements PositionManager {

  Cache<SourcePosition, Location> karateToJava = CacheBuilder.newBuilder()
    .expireAfterAccess(Duration.ofMinutes(60))
    .expireAfterWrite(Duration.ofMinutes(30))
    .maximumSize(5000)
    .build();

  Cache<Location, LinkedHashSet<JavaSourcePosition>> javaToKarate = CacheBuilder.newBuilder()
    .expireAfterAccess(Duration.ofMinutes(60))
    .expireAfterWrite(Duration.ofMinutes(30))
    .maximumSize(5000)
    .build();

  Cache<SourcePosition, List<Method>> karateToMethod = CacheBuilder.newBuilder()
    .expireAfterAccess(Duration.ofMinutes(60))
    .expireAfterWrite(Duration.ofMinutes(30))
    .maximumSize(5000)
    .build();

  private final UppercutClassLoader classLoader = UppercutClassLoader.INSTANCE;
  private final DebugProcessImpl debugProcess;

  public KaratePositionManager(DebugProcess debugProcess) {
    this.debugProcess = (DebugProcessImpl) debugProcess;
  }

  //  @Override public @NotNull CompletableFuture<SourcePosition> getSourcePositionAsync(@Nullable Location location) {
  //    return CompletableFuture.supplyAsync(() -> {
  //      try {
  //        return getSourcePosition(location);
  //      } catch (NoDataException e) {
  //        throw new RuntimeException(e);
  //      }
  //    });
  //  }
  //
  @Override
  public @Nullable SourcePosition getSourcePosition(@Nullable Location location) throws NoDataException {
    try {
      LinkedHashSet<JavaSourcePosition> sourcePositions = null;
      sourcePositions = javaToKarate.get(location, () -> new LinkedHashSet<>());
      return sourcePositions.isEmpty() ? null : sourcePositions.getFirst();
    } catch (ExecutionException e) {
      throw new RuntimeException(e);
    }
  }

  @SneakyThrows
  @Override
  public @NotNull List<ReferenceType> getAllClasses(@NotNull SourcePosition sourcePosition)
    throws NoDataException {
    VirtualMachine vm = debugProcess.getVirtualMachineProxy().getVirtualMachine();
    return getReferenceTypes(getMethodsFromText(sourcePosition), vm).stream().toList();
  }

  private List<Method> getMethodsFromText(@NotNull SourcePosition sourcePosition) {
    try {
      Class<?> stepRuntimeClass = classLoader.getClass("com.intuit.karate.core.StepRuntime");
      if (stepRuntimeClass == null) {
        return List.of();
      }
      Method match = stepRuntimeClass.getDeclaredMethod("findMethodsMatching", String.class);
      match.setAccessible(true);
      List<Object> methodMatch =
        (List<Object>) match.invoke(stepRuntimeClass,
          ApplicationManager.getApplication().runReadAction(
            (ThrowableComputable<String, RuntimeException>) () -> dealWithSteps(sourcePosition.getElementAt())));

      return methodMatch.stream().map(m -> {
        try {
          Field method = m.getClass().getDeclaredField("method");
          method.setAccessible(true);
          return (Method) (method.get(m));
        } catch (IllegalAccessException | NoSuchFieldException e) {
          throw new RuntimeException(e);
        }
      }).toList();
    } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
      log.error("Unable to find methods matching text", e);
      return List.of();
    }
  }

  private String dealWithSteps(final PsiElement element) {
    String text = element.getText().trim();
    PsiElement cur = element;
    if (cur.getParent() != null) {
      cur = element.getParent();
    }
    String[] stripOffGivenWhenThen = cur.getText().split(" ", 2);
    if (stripOffGivenWhenThen.length > 1) {
      return stripOffGivenWhenThen[1];
    }
    return text;
  }

  public static Set<ReferenceType> getReferenceTypes(Collection<Method> methods, VirtualMachine vm) {
    Set<ReferenceType> referenceTypes = new HashSet<>();

    for (Method method : methods) {
      String className = method.getDeclaringClass().getName();
      UppercutClassLoader.INSTANCE.getClass(className);
      List<ReferenceType> types = vm.classesByName(className);
      referenceTypes.addAll(types);
    }
    return referenceTypes;
  }


  @SneakyThrows
  @Override
  public @NotNull List<Location> locationsOfLine(@NotNull ReferenceType referenceType,
    @NotNull final SourcePosition sourcePosition) {
    List<Method> method = getMethodsFromText(sourcePosition);
    List<Location> locs = new ArrayList<>();

    List<com.sun.jdi.Method> methods = referenceType.methodsByName(method.get(0).getName());
    locs.addAll(methods.stream().flatMap(m -> {
      try {
        return m.allLineLocations().stream();
      } catch (AbsentInformationException e) {
        throw new RuntimeException(e);
      }
    }).toList());

    int lambdaOrdinal = -1;
    if (DebuggerUtilsEx.isLambda(methods.get(0))) {
      int line = sourcePosition.getLine() + 1;
      Set<com.sun.jdi.Method> lambdas = StreamEx.of(locs.get(0).declaringType().methods())
        .filter(DebuggerUtilsEx::isLambda)
        .filter(m -> !DebuggerUtilsEx.locationsOfLine(m, line).isEmpty())
        .toSet();
      if (lambdas.size() > 1) {
        ArrayList<com.sun.jdi.Method> lambdasList = new ArrayList<>(lambdas);
        lambdasList.sort(DebuggerUtilsEx.LAMBDA_ORDINAL_COMPARATOR);
        lambdaOrdinal = lambdasList.indexOf(method);
      }
    }

    if (!locs.isEmpty()) {
      karateToJava.put(sourcePosition, locs.get(0));
      int finalLambdaOrdinal = lambdaOrdinal;
      locs.forEach(l -> {
        LinkedHashSet<JavaSourcePosition> sourcePositions =
          Optional.ofNullable(javaToKarate.getIfPresent(l)).orElse(new LinkedHashSet<>());
        sourcePositions.add(new JavaSourcePosition(sourcePosition, referenceType, methods.get(0), finalLambdaOrdinal));
        javaToKarate.put(l, sourcePositions);
      });
    }
    return List.of(locs.get(0));
  }

  @Nullable
  private String getOuterClassName(final SourcePosition position) {
    return ApplicationManager.getApplication().runReadAction((Computable<String>) () -> position.getFile().getName());
  }

  @Override
  public ClassPrepareRequest createPrepareRequest(@NotNull final ClassPrepareRequestor requestor,
    @NotNull final SourcePosition position)
    throws NoDataException {

    String queryerName = getOuterClassName(position);
    ClassPrepareRequestor waitRequestor = new ClassPrepareRequestor() {
      @Override
      public void processClassPrepare(DebugProcess debuggerProcess, ReferenceType referenceType) {
        final CompoundPositionManager positionManager = ((DebugProcessImpl) debuggerProcess).getPositionManager();
        if (!positionManager.locationsOfLine(referenceType, position).isEmpty()) {
          requestor.processClassPrepare(debuggerProcess, referenceType);
        }
      }
    };
    List<Method> allMethods = getMethodsFromText(position);
    if (!getReferenceTypes(allMethods, debugProcess.getVirtualMachineProxy().getVirtualMachine()).isEmpty()) {
      return debugProcess.getRequestsManager()
        .createClassPrepareRequest(requestor, queryerName);
    }

    ClassPrepareRequest classPrepareRequest = null;
    for (Method method : allMethods) {
      if (classPrepareRequest == null) {
        debugProcess.getRequestsManager()
          .callbackOnPrepareClasses(waitRequestor, method.getDeclaringClass().getName());
      }
    }
    ClassPrepareRequest classPrepareRequest1 = debugProcess.getRequestsManager()
      .createClassPrepareRequest(waitRequestor, position.getFile().getName() + "$*");
    return classPrepareRequest1;
  }

  @Override public @Nullable Set<? extends FileType> getAcceptedFileTypes() {
    return Set.of(GherkinFileType.INSTANCE);
  }

}
