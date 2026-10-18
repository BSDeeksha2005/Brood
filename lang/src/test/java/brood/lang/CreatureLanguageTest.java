package brood.lang;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import brood.lang.CreatureProgram.Action;
import org.junit.jupiter.api.Test;

class CreatureLanguageTest {
    private final CreatureLanguage language = new CreatureLanguage();
    private final ProgramInterpreter interpreter = new ProgramInterpreter();

    @Test
    void compilesCommentsConditionsActionsAndOtherwise() {
        CreatureProgram program = language.compile("# low energy first\nwhen energy <= 25000: rest // recover\notherwise: seek nearest_food");
        assertEquals(2, program.rules().size());
        assertEquals(Action.REST, program.rules().getFirst().action());
        assertTrue(program.rules().getLast().condition() instanceof CreatureProgram.Always);
    }

    @Test
    void reportsUsefulOneBasedSyntaxPosition() {
        CompileException exception = assertThrows(CompileException.class,
                () -> language.compile("when energy = 4: rest"));
        assertEquals(1, exception.diagnostics().getFirst().line());
        assertEquals(13, exception.diagnostics().getFirst().column());
    }

    @Test
    void sourceOrderWinsAndMissingFoodConditionDoesNotMatch() {
        CreatureProgram program = language.compile("when nearest_food < 100: eat\nwhen energy >= 0: rest");
        ProgramInterpreter.Execution noFood = interpreter.execute(program, 50, null, 8);
        assertEquals("rule-2", noFood.ruleId());
        assertEquals(Action.REST, noFood.action());
        ProgramInterpreter.Execution withFood = interpreter.execute(program, 50, 50L, 8);
        assertEquals("rule-1", withFood.ruleId());
        assertEquals(Action.EAT, withFood.action());
    }

    @Test
    void unmatchedProgramAndStepBudgetAreExplicit() {
        CreatureProgram program = language.compile("when energy < 10: rest");
        assertFalse(interpreter.execute(program, 10, null, 2).matched());
        assertThrows(ProgramInterpreter.StepBudgetExceededException.class,
                () -> interpreter.execute(program, 1, null, 1));
    }

    @Test
    void otherwiseMustBeLastAndOnlyAppearOnce() {
        assertThrows(CompileException.class,
                () -> language.compile("otherwise: rest\nwhen energy < 2: eat"));
        assertThrows(CompileException.class,
                () -> language.compile("otherwise: rest\notherwise: eat"));
    }

    @Test
    void ruleCountAndConstantLimitsAreValidated() {
        String tooMany = String.join("\n", java.util.Collections.nCopies(65, "when energy < 2: rest"));
        assertThrows(CompileException.class, () -> language.compile(tooMany));
        assertThrows(CompileException.class, () -> language.compile("when nearest_food < 1000001: eat"));
    }
}
