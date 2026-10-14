package Tests;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

public class TestCodeGen {

    private String codeGen(final String code) throws IOException {
        return ZFixtures.module(code);
    }

    private void assertCodegenFails(final String code) {
        assertNotNull(ZFixtures.failureOf(code), "expected the compilation to fail");
    }

    @Test
    public void testSimpleFunction() throws IOException {
        final String code = "proc test() { ret 3 + 4 }";

        assertEquals("""
                define private dso_local double @test() {
                entry:
                 %1 = add i32 3, 4
                 %2 = sitofp i32 %1 to double
                 ret double %2
                }
                """, codeGen(code));
    }

    @Test
    public void testFunctionWithReturnStatement() throws IOException {
        final String code = "proc main() { ret 40 + 32 }";

        assertEquals("""
                define dso_local i32 @main() {
                entry:
                 %1 = add i32 40, 32
                 ret i32 %1
                }
                """, codeGen(code));
    }

    @Test
    public void testFunctionWithReturnNonMain() throws IOException {
        final String code = "proc foo() { ret 10 * 2 }";

        assertEquals("""
                define private dso_local double @foo() {
                entry:
                 %1 = mul i32 10, 2
                 %2 = sitofp i32 %1 to double
                 ret double %2
                }
                """, codeGen(code));
    }

    @Test
    public void testVarsWithoutAssignment() throws IOException {
        final String code = "proc main() { int32 var \n ret 0 }";

        assertEquals("""
                define dso_local i32 @main() {
                entry:
                 %var = alloca i32
                 store i32 0, ptr %var
                 ret i32 0
                }
                """, codeGen(code));
    }

    @Test
    public void testVarsWithAssignment() throws IOException {
        final String code = "proc main() { int32 var -> 3 \n ret 0 }";

        assertEquals("""
                define dso_local i32 @main() {
                entry:
                 %var = alloca i32
                 store i32 3, ptr %var
                 ret i32 0
                }
                """, codeGen(code));
    }

    @Test
    public void testVarsWithAssignmentWithRet() throws IOException {
        final String code = "proc main() { int32 var -> 3 \n ret var }";

        assertEquals("""
                define dso_local i32 @main() {
                entry:
                 %var = alloca i32
                 store i32 3, ptr %var
                 %1 = load i32, ptr %var
                 ret i32 %1
                }
                """, codeGen(code));
    }

    @Test
    public void testMoreVars() throws IOException {
        final String code = "proc main() { int32 var\n int32 var2\n ret 0 }";

        assertEquals("""
                define dso_local i32 @main() {
                entry:
                 %var = alloca i32
                 store i32 0, ptr %var
                 %var2 = alloca i32
                 store i32 0, ptr %var2
                 ret i32 0
                }
                """, codeGen(code));
    }

    @Test
    public void testVarsWithDifferentIntegers() throws IOException {
        final String code = "proc main() { int32 var -> 3\n int64 var2 -> 4\n ret 0 }";

        assertEquals("""
                define dso_local i32 @main() {
                entry:
                 %var = alloca i32
                 store i32 3, ptr %var
                 %var2 = alloca i64
                 store i64 4, ptr %var2
                 ret i32 0
                }
                """, codeGen(code));
    }

    @Test
    public void testVarsWithDifferentTypesWithRet() throws IOException {
        final String code = "proc main() { int32 var -> 3\n flt32 var2 -> 4\n flt64 var3 -> 4\n ret var3 }";

        assertEquals("""
                define dso_local i32 @main() {
                entry:
                 %var = alloca i32
                 store i32 3, ptr %var
                 %var2 = alloca float
                 store float 4.0, ptr %var2
                 %var3 = alloca double
                 store double 4.0, ptr %var3
                 %1 = load double, ptr %var3
                 %2 = fptosi double %1 to i32
                 ret i32 %2
                }
                """, codeGen(code));
    }

    @Test
    public void testConstantDefinitionWithDifferentTypes() throws IOException {
        final String code = "proc main() { cn int32 const -> 3\n cn flt32 floatConst -> 1.0\n ret 0}";

        assertEquals("""
                define dso_local i32 @main() {
                entry:
                 %const = alloca i32
                 store i32 3, ptr %const
                 %floatConst = alloca float
                 store float 1.0, ptr %floatConst
                 ret i32 0
                }
                """, codeGen(code));
    }

    @Test
    public void testConstantDefinitionWithRet() throws IOException {
        final String code = "proc main() { cn int32 const -> 3\n cn flt32 floatConst -> 1.0\n ret const}";

        assertEquals("""
                define dso_local i32 @main() {
                entry:
                 %const = alloca i32
                 store i32 3, ptr %const
                 %floatConst = alloca float
                 store float 1.0, ptr %floatConst
                 %1 = load i32, ptr %const
                 ret i32 %1
                }
                """, codeGen(code));
    }

    @Test
    public void testFloatLiteralKeepsItsPoint() throws IOException {
        final String code = "proc main() { flt64 pi -> 3.14\n ret pi }";

        final String llvmIR = codeGen(code);

        assertNotNull(llvmIR);
        assertTrue(llvmIR.contains("store double 3.14,"), llvmIR);
    }

    @Test
    public void testLargeIntegerKeepsEveryDigit() throws IOException {
        final String code = "proc main() { int64 big -> 9007199254740993\n ret big }";

        final String llvmIR = codeGen(code);

        assertNotNull(llvmIR);
        assertTrue(llvmIR.contains("store i64 9007199254740993,"), llvmIR);
    }

    @Test
    public void testLargeIntegerIntoInt32Truncates() throws IOException {
        final String code = "proc main() { ret 9007199254740993 }";

        final String llvmIR = codeGen(code);

        assertNotNull(llvmIR);
        assertTrue(llvmIR.contains("ret i32 1"), llvmIR);
    }

    @Test
    public void testFunctionCallInsideMain() throws IOException {
        final String code = "proc foo() { ret 1 }\nproc main() { foo() \n ret 0 }";

        final String llvmIR = codeGen(code);

        assertNotNull(llvmIR);
        assertTrue(llvmIR.contains("call double @foo()"), llvmIR);
    }

    @Test
    public void testFunctionWithParameters() throws IOException {
        final String code = "proc foo(int32 x | int32 y) { ret x + y }";

        final String llvmIR = codeGen(code);

        assertNotNull(llvmIR);
        assertTrue(llvmIR.contains("@foo(i32 %x, i32 %y)"), llvmIR);
    }

    @Test
    public void testCallWithParameters() throws IOException {
        final String code = "proc foo(int32 x | int32 y) { ret x + y }\nproc main() { ret foo(1 | 2) }";

        final String llvmIR = codeGen(code);

        assertNotNull(llvmIR);
        assertTrue(llvmIR.contains("call double @foo(i32 1, i32 2)"), llvmIR);
    }

    @Test
    public void testSimpleForLoop() throws IOException {
        final String code = "proc main() {\n"
                + "  for (int32 i -> 0 | i < 10 | i + 1) {\n"
                + "    int32 x -> 5\n"
                + "  }\n"
                + "  ret 0\n"
                + "}";

        assertEquals("""
                define dso_local i32 @main() {
                entry:
                 %i = alloca i32
                 store i32 0, ptr %i
                 br label %for.cond.0
                 for.cond.0:
                 %1 = load i32, ptr %i
                 %2 = icmp slt i32 %1, 10
                 br i1 %2, label %for.body.1, label %for.end.3
                 for.body.1:
                 %x = alloca i32
                 store i32 5, ptr %x
                 br label %for.inc.2
                 for.inc.2:
                 %3 = load i32, ptr %i
                 %4 = add i32 %3, 1
                 store i32 %4, ptr %i
                 br label %for.cond.0
                 for.end.3:
                 ret i32 0
                }
                """, codeGen(code));
    }

    @Test
    public void testForLoopVariableIsReadableAfterTheLoop() throws IOException {
        final String code = "proc main() { for (int32 i -> 0 | i < 3 | i + 1) { }\n ret i }";

        final String llvmIR = codeGen(code);

        assertNotNull(llvmIR);
        assertTrue(llvmIR.contains("load i32, ptr %i"), llvmIR);
    }

    @Test
    public void testFloatForLoopComparesAsFloat() throws IOException {
        final String code = "proc main() {\n for (flt64 i -> 0.0 | i < 2.0 | i + 0.5) { }\n ret 0\n}";

        final String llvmIR = codeGen(code);

        assertNotNull(llvmIR);
        assertTrue(llvmIR.contains("fcmp olt double %1, 2.0"), llvmIR);
    }

    @Test
    public void testIntegerForLoopAgainstAFloatBoundIsComparedAsFloat() throws IOException {
        final String code = "proc main() {\n for (int32 i -> 0 | i < 2.5 | i + 1) { }\n ret 0\n}";

        final String llvmIR = codeGen(code);

        assertNotNull(llvmIR);
        assertTrue(llvmIR.contains("%2 = sitofp i32 %1 to double"), llvmIR);
        assertTrue(llvmIR.contains("fcmp olt double %2, 2.5"), llvmIR);
    }

    @Test
    public void testReturnInsideForLoopSkipsTheIncrement() throws IOException {
        final String code = "proc find(int32 n) {\n"
                + "  for (int32 i -> 0 | i < n | i + 1) {\n"
                + "    ret i\n"
                + "  }\n"
                + "  ret 0\n"
                + "}";

        final String llvmIR = codeGen(code);

        assertNotNull(llvmIR);
        assertTrue(llvmIR.contains("ret double"), llvmIR);
        assertTrue(!llvmIR.contains("for.inc"), llvmIR);
    }

    @Test
    public void testEmptyMainReturnsZero() throws IOException {
        assertEquals("""
                define dso_local i32 @main() {
                entry:
                 ret i32 0
                }
                """, codeGen("proc main() { }"));
    }

    @Test
    public void testUnaryMinusOnInt() throws IOException {
        final String llvmIR = codeGen("proc main() { ret -5 + 3 }");

        assertEquals("""
                define dso_local i32 @main() {
                entry:
                 %1 = sub i32 0, 5
                 %2 = add i32 %1, 3
                 ret i32 %2
                }
                """, llvmIR);
    }

    @Test
    public void testUnaryMinusOnFloat() throws IOException {
        final String llvmIR = codeGen("proc main() { ret -5.5 }");

        assertEquals("""
                define dso_local i32 @main() {
                entry:
                 %1 = fneg double 5.5
                 %2 = fptosi double %1 to i32
                 ret i32 %2
                }
                """, llvmIR);
    }

    @Test
    public void testDivisionOfIntegersStaysInteger() throws IOException {
        assertTrue(codeGen("proc main() { ret 7 / 2 }").contains("div i32 7, 2"));
    }

    @Test
    public void testDivisionOfFloatsUsesFloatingPoint() throws IOException {
        assertTrue(codeGen("proc main() { ret 7.0 / 2.0 }").contains("fdiv double 7.0, 2.0"));
    }

    @Test
    public void testComparisonWidensToInt32() throws IOException {
        final String llvmIR = codeGen("proc main() { ret 3 < 4 }");

        assertEquals("""
                define dso_local i32 @main() {
                entry:
                 %1 = icmp slt i32 3, 4
                 %2 = zext i1 %1 to i32
                 ret i32 %2
                }
                """, llvmIR);
    }

    @Test
    public void testIntegerArgumentIsConvertedForAFloatParameter() throws IOException {
        final String llvmIR = codeGen("proc half(flt64 x) { ret x / 2 }\nproc main() { ret half(21) }");

        assertTrue(llvmIR.contains("sitofp i32 21 to double"), llvmIR);
        assertTrue(llvmIR.contains("call double @half(double"), llvmIR);
    }

    @Test
    public void testRecursiveCall() throws IOException {
        final String llvmIR = codeGen("proc fact(int32 n) { ret n * fact(n - 1) }\nproc main() { ret fact(5) }");

        assertNotNull(llvmIR);
        assertTrue(llvmIR.contains("call double @fact(i32"), llvmIR);
    }

    @Test
    public void testCallNestedInAnArgument() throws IOException {
        final String llvmIR = codeGen(
                "proc a(int32 n) { ret n + 1 }\nproc b(int32 n) { ret a(a(n)) }\nproc main() { ret b(1) }");

        assertNotNull(llvmIR);
        assertEquals(2, countOccurrences(llvmIR, "call double @a("));
        assertTrue(llvmIR.contains("call double @a(i32 %3)"), llvmIR);
    }

    @Test
    public void testCallToAFunctionDefinedLaterInTheFile() throws IOException {
        final String llvmIR = codeGen("proc main() { ret twice(21) }\nproc twice(int32 x) { ret x * 2 }");

        assertNotNull(llvmIR);
        assertTrue(llvmIR.contains("call double @twice(i32 21)"), llvmIR);
    }

    @Test
    public void testCallResultIsConvertedForTheReturningType() throws IOException {
        final String llvmIR = codeGen("proc one() { ret 1 }\nproc main() { ret one() }");

        assertNotNull(llvmIR);
        assertTrue(llvmIR.contains("fptosi double %1 to i32"), llvmIR);
    }

    @Test
    public void testShadowedDeclarationFails() {
        final String diagnostics = ZFixtures.failureOf("proc main() { int32 x -> 1\n int32 x -> 2\n ret x }");

        assertNotNull(diagnostics);
        assertTrue(diagnostics.contains("Shadowing"), diagnostics);
    }

    @Test
    public void testUndeclaredVariableFails() {
        final String diagnostics = ZFixtures.failureOf("proc main() { ret ghost }");

        assertNotNull(diagnostics);
        assertTrue(diagnostics.contains("non dichiarata"), diagnostics);
    }

    @Test
    public void testCallToUnknownFunctionFails() {
        final String diagnostics = ZFixtures.failureOf("proc main() { ret nope(1) }");

        assertNotNull(diagnostics);
        assertTrue(diagnostics.contains("unknown function"), diagnostics);
    }

    @Test
    public void testCallWithTooManyArgumentsFails() {
        final String diagnostics =
                ZFixtures.failureOf("proc foo(int32 x) { ret x }\nproc main() { ret foo(1 | 2) }");

        assertNotNull(diagnostics);
        assertTrue(diagnostics.contains("takes 1 argument"), diagnostics);
    }

    @Test
    public void testCallWithTooFewArgumentsFails() throws IOException {
        assertCodegenFails("proc foo(int32 x | int32 y) { ret x }\nproc main() { ret foo(1) }");
    }

    @Test
    public void testFunctionThatFallsOffTheEndFails() {
        final String diagnostics = ZFixtures.failureOf("proc foo() { 3 + 4 }");

        assertNotNull(diagnostics);
        assertTrue(diagnostics.contains("without returning"), diagnostics);
    }

    @Test
    public void testStatementAfterReturnFails() throws IOException {
        assertCodegenFails("proc main() { ret 0\n ret 1 }");
    }

    @Test
    public void testDuplicateParameterFails() throws IOException {
        assertCodegenFails("proc foo(int32 x | int32 x) { ret x }");
    }

    @Test
    public void testFailedStatementStopsTheWholeFunction() {
        assertCodegenFails("proc main() { int32 a -> 1\n ret ghost }");
    }

    @Test
    public void testFailedInitialiserStopsTheDeclaration() {
        assertCodegenFails("proc main() { int32 a -> ghost\n ret 0 }");
    }

    @Test
    public void testShadowingAParameterFails() {
        assertCodegenFails("proc f(int32 x) { int32 x -> 1\n ret x }");
    }

    @Test
    public void testVariableUsedBeforeItIsDeclaredFails() {
        assertCodegenFails("proc main() { ret x\n int32 x -> 1 }");
    }

    @Test
    public void testTwoFunctionsWithTheSameNameFail() {
        assertCodegenFails("proc a() { ret 1 }\nproc a() { ret 2 }\nproc main() { ret a() }");
    }

    @Test
    public void testAFailureInsideALoopBodyFails() {
        assertCodegenFails("proc main() {\n for (int32 i -> 0 | i < 3 | i + 1) {\n  ret ghost\n }\n ret 0\n}");
    }

    @Test
    public void testAFailureInALoopInitialiserFails() {
        assertCodegenFails("proc main() {\n for (int32 i -> ghost | i < 3 | i + 1) { }\n ret 0\n}");
    }

    @Test
    public void testAFailureInALoopUpdateFails() {
        assertCodegenFails("proc main() {\n for (int32 i -> 0 | i < 3 | ghost) { }\n ret 0\n}");
    }

    @Test
    public void testAFailureInTheLoopConditionFails() {
        assertCodegenFails("proc main() {\n for (int32 i -> 0 | ghost | i + 1) { }\n ret 0\n}");
    }

    @Test
    public void testCallingTheSameFunctionTwiceEmitsTwoCalls() throws IOException {
        final String llvmIR = codeGen("proc a() { ret 1 }\nproc main() { ret a() + a() }");

        assertNotNull(llvmIR);
        assertEquals(2, countOccurrences(llvmIR, "call double @a()"));
    }

    @Test
    public void testACallWhoseResultIsDiscardedIsStillEmitted() throws IOException {
        final String llvmIR = codeGen("proc a() { ret 1 }\nproc main() { a()\n ret 0 }");

        assertNotNull(llvmIR);
        assertTrue(llvmIR.contains("call double @a()"), llvmIR);
    }

    @Test
    public void testAConstantCanBeAnArgument() throws IOException {
        final String llvmIR = codeGen(
                "proc add(int32 x | int32 y) { ret x + y }\nproc main() { cn int32 k -> 2\n ret add(40 | k) }");

        assertNotNull(llvmIR);
        assertTrue(llvmIR.contains("call double @add(i32 40, i32 %1)"), llvmIR);
    }

    @Test
    public void testMixingAnIntegerAndAFloatWidensToFloat() throws IOException {
        assertEquals("""
                define dso_local i32 @main() {
                entry:
                 %1 = sitofp i32 1 to double
                 %2 = fadd double %1, 2.5
                 %3 = fptosi double %2 to i32
                 ret i32 %3
                }
                """, codeGen("proc main() { ret 1 + 2.5 }"));
    }

    @Test
    public void testFloatVariableIsReadAsItsOwnType() throws IOException {
        assertEquals("""
                define dso_local i32 @main() {
                entry:
                 %a = alloca float
                 store float 1.5, ptr %a
                 %1 = load float, ptr %a
                 %2 = fptosi float %1 to i32
                 ret i32 %2
                }
                """, codeGen("proc main() { flt32 a -> 1.5\n ret a }"));
    }

    @Test
    public void testFloatVariableIsWidenedWhenUsedAsAnArgument() throws IOException {
        final String llvmIR = codeGen(
                "proc id(flt64 x) { ret x }\nproc main() { flt32 a -> 1.5\n ret id(a) }");

        assertNotNull(llvmIR);
        assertTrue(llvmIR.contains("fpext float %1 to double"), llvmIR);
    }

    @Test
    public void testEmptyFileProducesNoModule() throws IOException {
        assertEquals("", codeGen(""));
    }

    private static int countOccurrences(final String haystack, final String needle) {
        int count = 0;
        int at = haystack.indexOf(needle);
        while (at >= 0) {
            count++;
            at = haystack.indexOf(needle, at + needle.length());
        }
        return count;
    }

    @Test
    public void testSimpleIf() throws IOException {
        final String llvmIR = codeGen("proc main() { if (1 < 2) { ret 1 } ret 0 }");

        assertNotNull(llvmIR);
        assertTrue(llvmIR.contains("if.cond."), llvmIR);
        assertTrue(llvmIR.contains("if.then."), llvmIR);
        assertTrue(llvmIR.contains("if.end."), llvmIR);
        assertTrue(llvmIR.contains("icmp slt i32 1, 2"), llvmIR);
    }

    @Test
    public void testIfElse() throws IOException {
        final String llvmIR = codeGen(
                "proc main() { if (1 > 2) { ret 1 } else { ret 0 } }");

        assertNotNull(llvmIR);
        assertTrue(llvmIR.contains("if.else."), llvmIR);
    }

    @Test
    public void testIfEifElse() throws IOException {
        final String llvmIR = codeGen(
                "proc main() { if (1 > 2) { ret 1 } eif (2 > 3) { ret 2 } else { ret 3 } }");

        assertNotNull(llvmIR);
        // " if.cond." (leading space) matches only the label definition lines,
        // not the "br label %if.cond.N" lines.
        assertEquals(2, countOccurrences(llvmIR, " if.cond."));
        assertTrue(llvmIR.contains("if.else."), llvmIR);
    }

    @Test
    public void testIfWithFloatCondition() throws IOException {
        final String llvmIR = codeGen(
                "proc main() { if (1.0 < 2.0) { ret 1 } ret 0 }");

        assertNotNull(llvmIR);
        assertTrue(llvmIR.contains("fcmp olt double 1.0, 2.0"), llvmIR);
    }

    @Test
    public void testIfBranchWithoutReturnFallsThrough() throws IOException {
        // The outer then-branch does not return, so it must fall through
        // to the outer if.end.
        final String llvmIR = codeGen(
                "proc main() { if (1 < 2) { if (2 < 3) { ret 1 } }\n ret 0 }");

        assertNotNull(llvmIR);
        assertTrue(llvmIR.contains("br label %if.end."), llvmIR);
    }

    @Test
    public void testNestedIf() throws IOException {
        final String llvmIR = codeGen(
                "proc main() { if (1 < 2) { if (2 < 3) { ret 1 } } ret 0 }");

        assertNotNull(llvmIR);
        assertEquals(2, countOccurrences(llvmIR, " if.cond."));
    }

    @Test
    public void testIfWithReturnOnlySkipsEndBranch() throws IOException {
        final String llvmIR = codeGen(
                "proc main() { if (1 < 2) { ret 1 } ret 0 }");

        // The then-branch ends in `ret`, so no fallthrough br is emitted there.
        assertEquals("""
                define dso_local i32 @main() {
                entry:
                 br label %if.cond.0
                 if.cond.0:
                 %1 = icmp slt i32 1, 2
                 br i1 %1, label %if.then.1, label %if.end.2
                 if.then.1:
                 ret i32 1
                 if.end.2:
                 ret i32 0
                }
                """, llvmIR);
    }

    @Test
    public void testUnsignedDeclarations() throws IOException {
        final String llvmIR = codeGen("proc main() { uint32 a -> 3\n uint64 b -> 4\n ret 0 }");

        assertTrue(llvmIR.contains("%a = alloca i32"), llvmIR);
        assertTrue(llvmIR.contains("store i32 3, ptr %a"), llvmIR);
        assertTrue(llvmIR.contains("%b = alloca i64"), llvmIR);
        assertTrue(llvmIR.contains("store i64 4, ptr %b"), llvmIR);
    }

    @Test
    public void testUnsignedDivisionUsesUdiv() throws IOException {
        final String llvmIR = codeGen("proc main() { uint32 a -> 10\n uint32 b -> 3\n ret a / b }");
        assertTrue(llvmIR.contains("udiv i32"), llvmIR);
    }

    @Test
    public void testSignedDivisionStillUsesSdiv() throws IOException {
        final String llvmIR = codeGen("proc main() { int32 a -> 10\n int32 b -> 3\n ret a / b }");
        assertTrue(llvmIR.contains("sdiv i32"), llvmIR);
    }

    @Test
    public void testUnsignedComparisonUsesUlt() throws IOException {
        final String llvmIR = codeGen("proc main() { uint32 a -> 10\n ret a < 5 }");
        assertTrue(llvmIR.contains("icmp ult i32"), llvmIR);
    }

    @Test
    public void testSignedComparisonStillUsesSlt() throws IOException {
        final String llvmIR = codeGen("proc main() { int32 a -> 10\n ret a < 5 }");
        assertTrue(llvmIR.contains("icmp slt i32"), llvmIR);
    }

    @Test
    public void testUnsignedWidensWithZext() throws IOException {
        final String llvmIR = codeGen("proc main() { uint32 a -> 1\n uint64 b -> a\n ret 0 }");
        assertTrue(llvmIR.contains("zext i32"), llvmIR);
    }

    @Test
    public void testSignedWidensWithSext() throws IOException {
        final String llvmIR = codeGen("proc main() { int32 a -> 1\n int64 b -> a\n ret 0 }");
        assertTrue(llvmIR.contains("sext i32"), llvmIR);
    }

    @Test
    public void testUnsignedToFloatUsesUitofp() throws IOException {
        final String llvmIR = codeGen("proc main() { uint32 a -> 1\n flt64 b -> a\n ret 0 }");
        assertTrue(llvmIR.contains("uitofp i32"), llvmIR);
    }

    @Test
    public void testSignedToFloatUsesSitofp() throws IOException {
        final String llvmIR = codeGen("proc main() { int32 a -> 1\n flt64 b -> a\n ret 0 }");
        assertTrue(llvmIR.contains("sitofp i32"), llvmIR);
    }

    @Test
    public void testStringDeclarationStartsWithNull() throws IOException {
        final String llvmIR = codeGen("proc main() { str s\n ret 0 }");

        assertTrue(llvmIR.contains("%s = alloca i8*"), llvmIR);
        assertTrue(llvmIR.contains("store i8* null, ptr %s"), llvmIR);
    }

    @Test
    public void testStringParameter() throws IOException {
        final String llvmIR = codeGen("proc take(str s) { ret 0 }");
        assertTrue(llvmIR.contains("@take(i8* %s)"), llvmIR);
    }

    @Test
    public void testUint32Parameter() throws IOException {
        final String llvmIR = codeGen("proc take(uint32 n) { ret 0 }");
        assertTrue(llvmIR.contains("@take(i32 %n)"), llvmIR);
    }

    @Test
    public void testStringLiteralIsPutInAGlobal() throws IOException {
        final String llvmIR = codeGen("proc main() { str s -> \"hello\"\n ret 0 }");

        assertNotNull(llvmIR);
        assertTrue(llvmIR.contains("@.str.0 = private unnamed_addr constant [6 x i8] c\"hello\\00\""), llvmIR);
        assertTrue(llvmIR.contains("getelementptr inbounds [6 x i8], ptr @.str.0"), llvmIR);
    }

    @Test
    public void testStringDeclarationStoresThePointer() throws IOException {
        final String llvmIR = codeGen("proc main() { str s -> \"hi\"\n ret 0 }");

        assertNotNull(llvmIR);
        assertTrue(llvmIR.contains("%s = alloca i8*"), llvmIR);
        assertTrue(llvmIR.contains("store i8* %1, ptr %s"), llvmIR);
    }

    @Test
    public void testStringConcatenationUsesLibc() throws IOException {
        final String llvmIR = codeGen(
                "proc main() { str a -> \"hello\"\n str b -> \" world\"\n str c -> a + b\n ret 0 }");

        assertNotNull(llvmIR);
        assertTrue(llvmIR.contains("declare ptr @malloc(i64)"), llvmIR);
        assertTrue(llvmIR.contains("declare i64 @strlen(ptr)"), llvmIR);
        assertTrue(llvmIR.contains("declare ptr @strcpy(ptr, ptr)"), llvmIR);
        assertTrue(llvmIR.contains("declare ptr @strcat(ptr, ptr)"), llvmIR);
        assertTrue(llvmIR.contains("call i64 @strlen(ptr"), llvmIR);
        assertTrue(llvmIR.contains("call ptr @malloc(i64"), llvmIR);
        assertTrue(llvmIR.contains("call ptr @strcpy(ptr"), llvmIR);
        assertTrue(llvmIR.contains("call ptr @strcat(ptr"), llvmIR);
    }

    @Test
    public void testProgramWithoutStringsHasNoLibcDeclares() throws IOException {
        final String llvmIR = codeGen("proc main() { ret 0 }");

        assertFalse(llvmIR.contains("declare ptr @malloc"), llvmIR);
        assertFalse(llvmIR.contains("@.str."), llvmIR);
    }

    @Test
    public void testStringArgumentPassesAPointer() throws IOException {
        final String llvmIR = codeGen(
                "proc id(str s) { ret 0 }\nproc main() { ret 0 }");

        assertTrue(llvmIR.contains("@id(i8* %s)"), llvmIR);
    }
}