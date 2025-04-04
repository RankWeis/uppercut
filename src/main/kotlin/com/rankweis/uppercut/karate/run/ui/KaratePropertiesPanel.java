/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.rankweis.uppercut.karate.run.ui;

import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.AddEditRemovePanel;
import com.rankweis.uppercut.MyBundle;
import java.awt.Dimension;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class KaratePropertiesPanel extends AddEditRemovePanel<Pair<String, String>> {

  public KaratePropertiesPanel(Map<String, String> availableProperties) {
    super(new MyPropertiesTableModel(), new ArrayList<>(), null);
    setPreferredSize(new Dimension(100, 100));
  }

  @Override
  protected Pair<String, String> addItem() {
    return doAddOrEdit(null);
  }

  @Override
  protected boolean removeItem(Pair<String, String> o) {
    return true;
  }

  @Override
  protected Pair<String, String> editItem(@NotNull Pair<String, String> o) {
    return doAddOrEdit(o);
  }

  @Nullable
  private Pair<String, String> doAddOrEdit(@Nullable Pair<String, String> o) {
    return o;
  }

  public Map<String, String> getDataAsMap() {
    Map<String, String> result = new LinkedHashMap<>();
    for (Pair<String, String> p : getData()) {
      result.put(p.getFirst(), p.getSecond());
    }
    return result;
  }

  public void setDataFromMap(Map<String, String> map) {
    List<Pair<String, String>> result = new ArrayList<>();
    for (Map.Entry<String, String> e : map.entrySet()) {
      result.add(Pair.create(e.getKey(), e.getValue()));
    }
    setData(result);
  }

  private static class MyPropertiesTableModel extends AddEditRemovePanel.TableModel<Pair<String, String>> {
    @Override
    public int getColumnCount() {
      return 2;
    }

    @Override
    @NlsContexts.ColumnName
    public String getColumnName(int c) {
      return c == 0 ? MyBundle.message("column.name.name") : MyBundle.message("column.name.value");
    }

    @Override
    public Object getField(Pair<String, String> o, int c) {
      return c == 0 ? o.getFirst() : o.getSecond();
    }
  }

}