// Standalone sample project - intentionally NOT included in the root uppercut build.
//
// Two modules on two different Karate majors, in one project on purpose: it mirrors a monorepo
// where teams migrate at different times, and it lets the UI test cover both runner paths in a
// single IDE session. Version detection is per-module (KarateRunConfiguration#karateLibraryRoots),
// so each module gets the runner its own classpath calls for.
rootProject.name = "karate-versions"

include(":v1", ":v2")
