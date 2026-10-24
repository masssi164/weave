package com.massimotter.weave.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class MemberTestScenarioTest {
  @Test
  void javaPreparationConsumesTheSameVersionedRecipeAsNativeAndDogfood() {
    MemberTestScenario first = MemberTestScenario.resolve(Path.of(".."), "member-run-one");
    MemberTestScenario second = MemberTestScenario.resolve(Path.of(".."), "member-run-two");
    assertEquals("workspace-default", first.spaceRef());
    assertEquals("member-a", first.actorLogicalId("A"));
    assertEquals("member-b", first.actorLogicalId("B"));
    assertEquals("member", first.resolved().path("actors").get(0).path("organizationRole").asString());
    assertEquals("member", first.resolved().path("actors").get(1).path("organizationRole").asString());
    assertEquals("weave-dual-initial-member-run-one.txt", first.content("initialFileName"));
    assertEquals("Weave actual UI initial member-run-one\n", first.content("initialContent"));
    assertEquals(first.resolved().path("scenarioSha256"), second.resolved().path("scenarioSha256"));
    assertNotEquals(first.content("conversation"), second.content("conversation"));
    assertThrows(ProductFlowException.class, () -> first.actorLogicalId("owner"));
    assertThrows(ProductFlowException.class, () -> first.content("missing"));
  }

  @Test
  void compilerRejectsInvalidRunIdentityBeforePublishingAnyFixture() {
    ProductFlowException failure = assertThrows(ProductFlowException.class,
        () -> MemberTestScenario.resolve(Path.of(".."), "../owner-data"));
    assertEquals("Shared member scenario resolution failed", failure.getMessage());
  }
}
