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
                define internal dso_local double @test() alwaysinline nounwind nosync {
                entry:
                 %1 = add nsw i32 3, 4
                 %2 = sitofp i32 %1 to double
                 ret double %2
                }
                """, codeGen(code));
    }

    @Test
    public void testFunctionWithReturnStatement() throws IOException {
        final String code = "proc main() { ret 40 + 32 }";

        assertEquals("""
                define dso_local i32 @main() alwaysinline nounwind nosync hot willreturn {
                entry:
                 %1 = add nsw i32 40, 32
                 ret i32 %1
                }
                """, codeGen(code));
    }

    @Test
    public void testFunctionWithReturnNonMain() throws IOException {
        final String code = "proc foo() { ret 10 * 2 }";

        assertEquals("""
                define internal dso_local double @foo() alwaysinline nounwind nosync {
                entry:
                 %1 = mul nsw i32 10, 2
                 %2 = sitofp i32 %1 to double
                 ret double %2
                }
                """, codeGen(code));
    }

    @Test
    public void testVarsWithoutAssignment() throws IOException {
        final String code = "proc main() { int32 var \n ret 0 }";

        assertEquals("""
                !5000 = !{!"Z TBAA"}
                !5001 = !{!"omnipotent char", !5000, i64 0}
                !5002 = !{!"i32", !5001, i64 0}
                
                define dso_local i32 @main() alwaysinline nounwind nosync hot willreturn {
                entry:
                 %var = alloca i32, align 4
                 store i32 0, ptr %var, align 4, !tbaa !5002
                 ret i32 0
                }
                """, codeGen(code));
    }

    @Test
    public void testVarsWithAssignment() throws IOException {
        final String code = "proc main() { int32 var -> 3 \n ret 0 }";

        assertEquals("""
                !5000 = !{!"Z TBAA"}
                !5001 = !{!"omnipotent char", !5000, i64 0}
                !5002 = !{!"i32", !5001, i64 0}
                
                define dso_local i32 @main() alwaysinline nounwind nosync hot willreturn {
                entry:
                 %var = alloca i32, align 4
                 store i32 3, ptr %var, align 4, !tbaa !5002
                 ret i32 0
                }
                """, codeGen(code));
    }

    @Test
    public void testVarsWithAssignmentWithRet() throws IOException {
        final String code = "proc main() { int32 var -> 3 \n ret var }";

        assertEquals("""
                !5000 = !{!"Z TBAA"}
                !5001 = !{!"omnipotent char", !5000, i64 0}
                !5002 = !{!"i32", !5001, i64 0}
                
                define dso_local i32 @main() alwaysinline nounwind nosync hot willreturn {
                entry:
                 %var = alloca i32, align 4
                 store i32 3, ptr %var, align 4, !tbaa !5002
                 %1 = load i32, ptr %var, align 4, !tbaa !5002
                 ret i32 %1
                }
                """, codeGen(code));
    }

    @Test
    public void testMoreVars() throws IOException {
        final String code = "proc main() { int32 var\n int32 var2\n ret 0 }";

        assertEquals("""
                !5000 = !{!"Z TBAA"}
                !5001 = !{!"omnipotent char", !5000, i64 0}
                !5002 = !{!"i32", !5001, i64 0}
                
                define dso_local i32 @main() alwaysinline nounwind nosync hot willreturn {
                entry:
                 %var = alloca i32, align 4
                 %var2 = alloca i32, align 4
                 store i32 0, ptr %var, align 4, !tbaa !5002
                 store i32 0, ptr %var2, align 4, !tbaa !5002
                 ret i32 0
                }
                """, codeGen(code));
    }

    @Test
    public void testVarsWithDifferentIntegers() throws IOException {
        final String code = "proc main() { int32 var -> 3\n int64 var2 -> 4\n ret 0 }";

        assertEquals("""
                !5000 = !{!"Z TBAA"}
                !5001 = !{!"omnipotent char", !5000, i64 0}
                !5002 = !{!"i32", !5001, i64 0}
                !5003 = !{!"i64", !5001, i64 0}
                
                define dso_local i32 @main() alwaysinline nounwind nosync hot willreturn {
                entry:
                 %var = alloca i32, align 4
                 %var2 = alloca i64, align 8
                 store i32 3, ptr %var, align 4, !tbaa !5002
                 store i64 4, ptr %var2, align 8, !tbaa !5003
                 ret i32 0
                }
                """, codeGen(code));
    }

    @Test
    public void testVarsWithDifferentTypesWithRet() throws IOException {
        final String code = "proc main() { int32 var -> 3\n flt32 var2 -> 4\n flt64 var3 -> 4\n ret var3 }";

        assertEquals("""
                !5000 = !{!"Z TBAA"}
                !5001 = !{!"omnipotent char", !5000, i64 0}
                !5002 = !{!"i32", !5001, i64 0}
                !5003 = !{!"float", !5001, i64 0}
                !5004 = !{!"double", !5001, i64 0}
                
                define dso_local i32 @main() alwaysinline nounwind nosync hot willreturn {
                entry:
                 %var = alloca i32, align 4
                 %var2 = alloca float, align 4
                 %var3 = alloca double, align 8
                 store i32 3, ptr %var, align 4, !tbaa !5002
                 store float 4.0, ptr %var2, align 4, !tbaa !5003
                 store double 4.0, ptr %var3, align 8, !tbaa !5004
                 %1 = load double, ptr %var3, align 8, !tbaa !5004
                 %2 = fptosi double %1 to i32
                 ret i32 %2
                }
                """, codeGen(code));
    }

    @Test
    public void testConstantDefinitionWithDifferentTypes() throws IOException {
        final String code = "proc main() { cn int32 const -> 3\n cn flt32 floatConst -> 1.0\n ret 0}";

        assertEquals("""
                !5000 = !{!"Z TBAA"}
                !5001 = !{!"omnipotent char", !5000, i64 0}
                !5002 = !{!"i32", !5001, i64 0}
                !5003 = !{!"float", !5001, i64 0}
                
                define dso_local i32 @main() alwaysinline nounwind nosync hot willreturn {
                entry:
                 %const = alloca i32, align 4
                 %floatConst = alloca float, align 4
                 store i32 3, ptr %const, align 4, !tbaa !5002
                 store float 1.0, ptr %floatConst, align 4, !tbaa !5003
                 ret i32 0
                }
                """, codeGen(code));
    }

    @Test
    public void testConstantDefinitionWithRet() throws IOException {
        final String code = "proc main() { cn int32 const -> 3\n cn flt32 floatConst -> 1.0\n ret const}";

        assertEquals("""
                !5000 = !{!"Z TBAA"}
                !5001 = !{!"omnipotent char", !5000, i64 0}
                !5002 = !{!"i32", !5001, i64 0}
                !5003 = !{!"float", !5001, i64 0}
                
                define dso_local i32 @main() alwaysinline nounwind nosync hot willreturn {
                entry:
                 %const = alloca i32, align 4
                 %floatConst = alloca float, align 4
                 store i32 3, ptr %const, align 4, !tbaa !5002
                 store float 1.0, ptr %floatConst, align 4, !tbaa !5003
                 %1 = load i32, ptr %const, align 4, !tbaa !5002
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
        final String code = """
                 proc main() {
                  for (int32 i -> 0 | i < 10 | i + 1) {
                    int32 x -> 5
                  }
                  ret 0
                }""";

        assertEquals("""
!5000 = !{!"Z TBAA"}
!5001 = !{!"omnipotent char", !5000, i64 0}
!5002 = !{!"i32", !5001, i64 0}
!1001 = !{!1001, !1002, !1003}
!1002 = !{!"llvm.loop.vectorize.enable", i1 true}
!1003 = !{!"llvm.loop.unroll.count", i32 4}

define dso_local i32 @main() alwaysinline nounwind nosync hot willreturn {
entry:
 %i = alloca i32, align 4
 %x = alloca i32, align 4
 store i32 0, ptr %i, align 4, !tbaa !5002
 br label %for.cond.0
 for.cond.0:
 %1 = load i32, ptr %i, align 4, !tbaa !5002
 %2 = icmp slt i32 %1, 10
 br i1 %2, label %for.body.1, label %for.end.3, !prof !{!"branch_weights", i32 1000, i32 1}
 for.body.1:
 store i32 5, ptr %x, align 4, !tbaa !5002
 br label %for.inc.2
 for.inc.2:
 %3 = load i32, ptr %i, align 4, !tbaa !5002
 %4 = add nsw i32 %3, 1
 store i32 %4, ptr %i, align 4, !tbaa !5002
 br label %for.cond.0, !llvm.loop !1001
 for.end.3:
 ret i32 0
}
                """, codeGen(code));
    }

    @Test
    public void testForLoopVariableIsScopedToTheLoop() {
        final String code = "proc main() { for (int32 i -> 0 | i < 3 | i + 1) { }\n ret i }";
        assertCodegenFails(code);
    }
    @Test
    public void testOuterVariableRemainsAccessibleAfterInnerBlock() throws IOException {
        final String code = "proc main() { int32 x -> 3\n if (true) { int32 y -> 4 }\n ret x }";
        assertNotNull(codeGen(code));
    }
    @Test
    public void testVariableDeclaredInsideIfIsOutOfScope() {
        final String code = "proc main() { if (true) { int32 x -> 3 } ret x }";
        assertCodegenFails(code);
    }
    @Test
    public void testOuterVariableRemainsAccessibleInsideIfBlock() throws IOException {
        final String code = "proc main() { int32 x -> 3 if (true) { ret x } }";
        assertNotNull(codeGen(code));
    }
    @Test
    public void testInnerIfVariableIsNotAccessibleInOuterIf() {
        final String code = "proc main() { if (true) { if (true) { int32 x -> 3 } ret x } }";
        assertCodegenFails(code);
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
        final String code = """
                proc find(int32 n) {
                  for (int32 i -> 0 | i < n | i + 1) {
                    ret i
                  }
                  ret 0
                }""";

        final String llvmIR = codeGen(code);

        assertNotNull(llvmIR);
        assertTrue(llvmIR.contains("ret double"), llvmIR);
        assertFalse(llvmIR.contains("for.inc"), llvmIR);
    }

    @Test
    public void testBreakJumpsToTheEndOfTheLoop() throws IOException {
        final String code = """
                proc main() {
                  for (int32 i -> 0 | i < 10 | i + 1) {
                    if (i == 3) { break }
                  }
                  ret 0
                }""";

        final String llvmIR = codeGen(code);

        assertNotNull(llvmIR);
        assertTrue(llvmIR.contains("br label %for.end.3"), llvmIR);
    }

    @Test
    public void testContinueJumpsToTheUpdate() throws IOException {
        final String code = """
                proc main() {
                  for (int32 i -> 0 | i < 10 | i + 1) {
                    if (i == 3) { continue }
                    int32 x -> i
                  }
                  ret 0
                }""";

        final String llvmIR = codeGen(code);

        assertNotNull(llvmIR);
        assertEquals(2, llvmIR.split("br label %for.inc.2", -1).length - 1, llvmIR);
    }

    @Test
    public void testContinueAsTheLastStatementKeepsTheUpdate() throws IOException {
        final String code = "proc main() {\n for (int32 i -> 0 | i < 10 | i + 1) {\n  continue\n }\n ret 0\n}";

        final String llvmIR = codeGen(code);

        assertNotNull(llvmIR);
        assertTrue(llvmIR.contains("for.inc.2:"), llvmIR);
    }

    @Test
    public void testBreakLeavesOnlyTheInnermostLoop() throws IOException {
        final String code = """
                proc main() {
                  for (int32 i -> 0 | i < 3 | i + 1) {
                    for (int32 j -> 0 | j < 3 | j + 1) {
                      break
                    }
                  }
                  ret 0
                }""";

        final String llvmIR = codeGen(code);

        assertNotNull(llvmIR);
        assertTrue(llvmIR.contains("br label %for.end.7"), llvmIR);
        assertFalse(llvmIR.contains("br label %for.end.3"), llvmIR);
    }

    @Test
    public void testBreakOutsideALoopFails() {
        assertCodegenFails("proc main() {\n break\n ret 0\n}");
    }

    @Test
    public void testContinueOutsideALoopFails() {
        assertCodegenFails("proc main() {\n continue\n ret 0\n}");
    }

    @Test
    public void testStatementAfterBreakFails() {
        assertCodegenFails("""
                proc main() {
                  for (int32 i -> 0 | i < 3 | i + 1) {
                    break
                    int32 x -> 1
                  }
                  ret 0
                }""");
    }

    @Test
    public void testEmptyMainReturnsZero() throws IOException {
        assertEquals("""
                define dso_local i32 @main() alwaysinline nounwind nosync hot willreturn {
                entry:
                 ret i32 0
                }
                """, codeGen("proc main() { }"));
    }

    @Test
    public void testUnaryMinusOnInt() throws IOException {
        final String llvmIR = codeGen("proc main() { ret -5 + 3 }");

        assertEquals("""
                define dso_local i32 @main() alwaysinline nounwind nosync hot willreturn {
                entry:
                 %1 = sub i32 0, 5
                 %2 = add nsw i32 %1, 3
                 ret i32 %2
                }
                """, llvmIR);
    }

    @Test
    public void testUnaryMinusOnFloat() throws IOException {
        final String llvmIR = codeGen("proc main() { ret -5.5 }");

        assertEquals("""
                define dso_local i32 @main() alwaysinline nounwind nosync hot willreturn {
                entry:
                 %1 = fneg contract double 5.5
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
        assertTrue(codeGen("proc main() { ret 7.0 / 2.0 }").contains("fdiv contract double 7.0, 2.0"));
    }

    @Test
    public void testComparisonWidensToInt32() throws IOException {
        final String llvmIR = codeGen("proc main() { ret 3 < 4 }");

        assertEquals("""
                define dso_local i32 @main() alwaysinline nounwind nosync hot willreturn {
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
        assertTrue(diagnostics.contains("local"), diagnostics);
    }

    @Test
    public void testUndeclaredVariableFails() {
        final String diagnostics = ZFixtures.failureOf("proc main() { ret ghost }");

        assertNotNull(diagnostics);
        assertTrue(diagnostics.contains("undeclared"), diagnostics);
    }

    @Test
    public void testCallToUnknownFunctionFails() {
        final String diagnostics = ZFixtures.failureOf("proc main() { ret nope(1) }");

        assertNotNull(diagnostics);
        assertTrue(diagnostics.contains("unknown function"), diagnostics);
    }

    @Test
    public void testCallWithTooManyArgumentsFails() {
        final String diagnostics = ZFixtures.failureOf("proc foo(int32 x) { ret x }\nproc main() { ret foo(1 | 2) }");

        assertNotNull(diagnostics);
        assertTrue(diagnostics.contains("takes 1 argument"), diagnostics);
    }

    @Test
    public void testCallWithTooFewArgumentsFails() {
        assertCodegenFails("proc foo(int32 x | int32 y) { ret x }\nproc main() { ret foo(1) }");
    }

    @Test
    public void testFunctionThatFallsOffTheEndFails() {
        final String diagnostics = ZFixtures.failureOf("proc foo() { 3 + 4 }");

        assertNotNull(diagnostics);
        assertTrue(diagnostics.contains("without returning"), diagnostics);
    }

    @Test
    public void testStatementAfterReturnFails() {
        assertCodegenFails("proc main() { ret 0\n ret 1 }");
    }

    @Test
    public void testDuplicateParameterFails() {
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
                define dso_local i32 @main() alwaysinline nounwind nosync hot willreturn {
                entry:
                 %1 = fadd contract double 1.0, 2.5
                 %2 = fptosi double %1 to i32
                 ret i32 %2
                }
                """, codeGen("proc main() { ret 1 + 2.5 }"));
    }

    @Test
    public void testFloatVariableIsReadAsItsOwnType() throws IOException {
        assertEquals("""
                !5000 = !{!"Z TBAA"}
                !5001 = !{!"omnipotent char", !5000, i64 0}
                !5002 = !{!"float", !5001, i64 0}
                
                define dso_local i32 @main() alwaysinline nounwind nosync hot willreturn {
                entry:
                 %a = alloca float, align 4
                 store float 1.5, ptr %a, align 4, !tbaa !5002
                 %1 = load float, ptr %a, align 4, !tbaa !5002
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
                define dso_local i32 @main() alwaysinline nounwind nosync hot willreturn {
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
    public void testIfElseWithRet() throws IOException {
        final String llvmIR = codeGen("proc main() { if (1 < 2) {\n} else {\n ret 0\n}ret 1\n}\n");

        assertNotNull(llvmIR);
        assertEquals("""
                define dso_local i32 @main() alwaysinline nounwind nosync hot willreturn {
                entry:
                 br label %if.cond.0
                 if.cond.0:
                 %1 = icmp slt i32 1, 2
                 br i1 %1, label %if.then.1, label %if.else.2
                 if.then.1:
                 br label %if.end.3
                 if.else.2:
                 ret i32 0
                 if.end.3:
                 ret i32 1
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
    public void testStringDeclarationStartsWithZeroInitializer() throws IOException {
        final String llvmIR = codeGen("proc main() { str s\n ret 0 }");

        assertTrue(llvmIR.contains("%s = alloca { i8*, i64 }"), llvmIR);
        assertTrue(llvmIR.contains("store { i8*, i64 } zeroinitializer, ptr %s"), llvmIR);
    }

    @Test
    public void testStringParameter() throws IOException {
        final String llvmIR = codeGen("proc take(str s) { ret 0 }");
        assertTrue(llvmIR.contains("@take({ i8*, i64 } %s)"), llvmIR);
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
        assertTrue(llvmIR.contains("@.str.0.bytes = private unnamed_addr constant [6 x i8] c\"hello\\00\""), llvmIR);
        assertTrue(llvmIR.contains("@.str.0 = private unnamed_addr constant { i8*, i64 }"), llvmIR);
        assertTrue(llvmIR.contains("load { i8*, i64 }, ptr @.str.0"), llvmIR);
    }

    @Test
    public void testStringDeclarationStoresTheFatStruct() throws IOException {
        final String llvmIR = codeGen("proc main() { str s -> \"hi\"\n ret 0 }");

        assertNotNull(llvmIR);
        assertTrue(llvmIR.contains("%s = alloca { i8*, i64 }"), llvmIR);
        assertTrue(llvmIR.contains("store { i8*, i64 } %"), llvmIR);
    }

    @Test
    public void testStringConcatenationUsesTheAllocator() throws IOException {
        final String llvmIR = codeGen(
                "proc main() { str a -> \"hello\"\n str b -> \" world\"\n str c -> a + b\n ret 0 }");

        assertNotNull(llvmIR);
        assertTrue(llvmIR.contains("@.arena = global [1048576 x i8] zeroinitializer"), llvmIR);
        assertTrue(llvmIR.contains("@.arena.used = global i64 0"), llvmIR);
        assertTrue(llvmIR.contains("@.free_list = global ptr null"), llvmIR);
        assertTrue(llvmIR.contains("define ptr @zalloc(i64 %size)"), llvmIR);
        assertTrue(llvmIR.contains("define void @zfree(ptr %p)"), llvmIR);
        // Lengths come from the struct, not from strlen.
        assertTrue(llvmIR.contains("extractvalue { i8*, i64 }"), llvmIR);
        assertFalse(llvmIR.contains("@strlen"), llvmIR);
        // The bytes are copied with llvm.memcpy.
        assertTrue(llvmIR.contains("call void @llvm.memcpy.p0.p0.i64(ptr"), llvmIR);
        // The result is built as a new fat struct.
        assertTrue(llvmIR.contains("insertvalue { i8*, i64 }"), llvmIR);
        assertTrue(llvmIR.contains("call ptr @zalloc(i64"), llvmIR);
    }

    @Test
    public void testProgramWithoutStringsHasNoArena() throws IOException {
        final String llvmIR = codeGen("proc main() { ret 0 }");

        assertFalse(llvmIR.contains("@.arena"), llvmIR);
        assertFalse(llvmIR.contains("@.str."), llvmIR);
    }

    @Test
    public void testStringArgumentPassesAFatStruct() throws IOException {
        final String llvmIR = codeGen(
                "proc id(str s) { ret 0 }\nproc main() { ret 0 }");

        assertTrue(llvmIR.contains("@id({ i8*, i64 } %s)"), llvmIR);
    }

    @Test
    public void testStrReturnTypeAppearsInSignature() throws IOException {
        final String llvmIR = codeGen(
                "proc greet(str s) -> str { ret s }\nproc main() { ret 0 }");

        assertNotNull(llvmIR);
        assertTrue(llvmIR.contains("define internal dso_local { i8*, i64 } @greet({ i8*, i64 } %s)"), llvmIR);
    }

    @Test
    public void testInt32ReturnType() throws IOException {
        final String llvmIR = codeGen("proc answer() -> int32 { ret 42 }");

        assertNotNull(llvmIR);
        assertTrue(llvmIR.contains("define internal dso_local i32 @answer()"), llvmIR);
        assertTrue(llvmIR.contains("ret i32 42"), llvmIR);
    }

    @Test
    public void testCallingAStrReturningFunction() throws IOException {
        final String llvmIR = codeGen(
                """
                        proc greet(str s) -> str { ret s }
                        proc main() { str r -> greet("hi")
                         ret 0 }""");

        assertNotNull(llvmIR);
        assertTrue(llvmIR.contains("call { i8*, i64 } @greet({ i8*, i64 }"), llvmIR);
    }

    @Test
    public void testReturningAnIntFromAStrFunctionFails() {
        final String diagnostics = ZFixtures.failureOf(
                "proc foo() -> str { ret 42 }");

        assertNotNull(diagnostics);
        assertTrue(diagnostics.contains("cannot convert between str and"), diagnostics);
    }

    @Test
    public void testReturningStrFromAnIntFunctionFails() {
        final String diagnostics = ZFixtures.failureOf(
                "proc foo() -> int32 { str s -> \"x\"\n ret s }");

        assertNotNull(diagnostics);
        assertTrue(diagnostics.contains("cannot convert between str and"), diagnostics);
    }

    @Test
    public void testMainWithStrReturnFallsOffWithZero() throws IOException {
        final String llvmIR = codeGen("proc main() -> str { }");

        assertNotNull(llvmIR);
        assertTrue(llvmIR.contains("ret { i8*, i64 } zeroinitializer"), llvmIR);
    }

    @Test
    public void testPointerParameterCodegen() throws IOException {
        final String llvmIR = codeGen(
                """
                        proc inc(mut int32* p) { ret 0 }
                        proc main() { int32 x -> 0
                         inc(&x)
                         ret 0 }""");

        assertNotNull(llvmIR);
        assertTrue(llvmIR.contains("define internal dso_local double @inc(ptr noalias %p)"), llvmIR);
        assertTrue(llvmIR.contains("call double @inc(ptr %x)"), llvmIR);
    }

    @Test
    public void testNonMutPointerParameterAllowsAlias() throws IOException {
        final String llvmIR = codeGen(
                """
                        proc read(int32* a | int32* b) { ret 0 }
                        proc main() { int32 x -> 0
                         read(&x | &x)
                         ret 0 }""");

        assertNotNull(llvmIR);
        assertTrue(llvmIR.contains("define internal dso_local double @read(ptr %a, ptr %b)"), llvmIR);
        assertFalse(llvmIR.contains("noalias"), llvmIR);
    }

    @Test
    public void testPassingAliasToMutPointerFails() {
        assertCodegenFails(
                """
                        proc copy(mut int32* dst | int32* src) { ret 0 }
                        proc main() { int32 x -> 0
                         copy(&x | &x)
                         ret 0 }""");
    }

    @Test
    public void testPassingDistinctVariablesToMutPointerSucceeds() throws IOException {
        final String llvmIR = codeGen(
                """
                        proc copy(mut int32* dst | int32* src) { ret 0 }
                        proc main() { int32 a -> 0
                         int32 b -> 1
                         copy(&a | &b)
                         ret 0 }""");

        assertNotNull(llvmIR);
        assertTrue(llvmIR.contains("define internal dso_local double @copy(ptr noalias %dst, ptr %src)"), llvmIR);
    }

    @Test
    public void testDerefInFunction() throws IOException {
        final String llvmIR = codeGen(
                """
                        proc get(int32* p) -> int32 { ret *p }
                        proc main() { int32 x -> 42
                         ret get(&x) }""");

        assertTrue(llvmIR.contains("load i32, ptr"), llvmIR);
    }

    @Test
    public void testAssignThroughPointer() throws IOException {
        final String llvmIR = codeGen(
                """
                        proc set(int32* p) -> int32 { *p -> 1
                         ret 0 }
                        proc main() { int32 x -> 0
                         set(&x)
                         ret x }""");

        assertNotNull(llvmIR, "compilation failed");
        assertTrue(llvmIR.contains("store i32 1"), llvmIR);
    }

    @Test
    public void testPointerTypeMismatchFails() {
        final String diagnostics = ZFixtures.failureOf(
                """
                        proc inc(int32* p) { ret 0 }
                        proc main() { flt64 x -> 0.0
                         inc(&x)
                         ret 0 }""");

        assertNotNull(diagnostics);
        assertTrue(diagnostics.contains("cannot convert between"), diagnostics);
    }

    @Test
    public void testVoidFunction() throws IOException {
        final String llvmIR = codeGen("proc test() -> void {\nint32 test -> 0\n}\nproc main() {\n}\n");
        assertNotNull(llvmIR, "compilation failed");
        assertTrue(llvmIR.contains("ret void"), llvmIR);
        assertTrue(llvmIR.contains("ret i32 0"), llvmIR);
    }

    @Test
    public void testEasterEggRet() throws IOException {
        final String ir = codeGen("proc main() {\nret\n}\n");
        assertNotNull(ir, "compilation failed");
        assertTrue(ir.contains("ret i32 281"), ir);
    }

    @Test
    public void testStructDeclarationAndFieldAccess() throws IOException {
        final String code = """
                struct Point { int32 x | int32 y }
                proc main() {
                    Point p -> Point{1 | 2}
                    ret p.x
                }""";

        final String llvmIR = codeGen(code);

        assertNotNull(llvmIR, "compilation failed");
        assertTrue(llvmIR.contains("%Point = type { i32, i32 }"), llvmIR);
        assertTrue(llvmIR.contains("insertvalue %Point undef, i32 1, 0"), llvmIR);
        assertTrue(llvmIR.contains("insertvalue %Point"), llvmIR);
        assertTrue(llvmIR.contains("getelementptr inbounds %Point"), llvmIR);
    }

    @Test
    public void testStructFieldAccessFailsOnUnknownField() {
        final String diagnostics = ZFixtures.failureOf("""
                struct Point { int32 x | int32 y }
                proc main() {
                    Point p -> Point{1 | 2}
                    ret p.z
                }""");

        assertNotNull(diagnostics);
        assertTrue(diagnostics.contains("has no field 'z'"), diagnostics);
    }

    @Test
    public void testFieldAccessOnNonStructFails() {
        final String diagnostics = ZFixtures.failureOf("""
                proc main() {
                    int32 x -> 1
                    ret x.field
                }""");
        assertNotNull(diagnostics);
        assertTrue(diagnostics.contains("is not a struct"), diagnostics);
    }

    @Test
    public void testFieldAccessOnUndeclaredVariableFails() {
        final String diagnostics = ZFixtures.failureOf(
                "proc main() { ret ghost.x }");
        assertNotNull(diagnostics);
        assertTrue(diagnostics.contains("undeclared variable"), diagnostics);
    }

    @org.junit.jupiter.api.Disabled("Needs postfix .field on (expr) — follow-up PR")
    @Test
    public void testFieldAccessNeedsVariableBase() {
        final String diagnostics = ZFixtures.failureOf("""
                struct Point { int32 x }
                proc main() {
                    ret (Point{1}).x
                }""");
        assertNotNull(diagnostics);
        assertTrue(diagnostics.contains("field access needs a variable base"), diagnostics);
    }

    @Test
    public void testFieldAccessOnNestedStruct() throws IOException {
        final String llvmIR = codeGen("""
                struct Inner { int32 v }
                struct Outer { Inner inner | int32 y }
                proc main() {
                    Outer o -> Outer{Inner{7} | 2}
                    ret o.inner.v
                }""");
        assertNotNull(llvmIR);
        assertTrue(llvmIR.contains("getelementptr inbounds %Outer"), llvmIR);
        assertTrue(llvmIR.contains("getelementptr inbounds %Inner"), llvmIR);
    }

    @Test
    public void testStructLiteralUnknownTypeFails() {
        final String diagnostics = ZFixtures.failureOf(
                "proc main() { ret Unknown{1} }");
        assertNotNull(diagnostics);
        assertTrue(diagnostics.contains("Unknown struct"), diagnostics);
    }

    @Test
    public void testStructLiteralWrongArityFails() {
        final String diagnostics = ZFixtures.failureOf("""
                struct Point { int32 x | int32 y }
                proc main() { ret Point{1} }""");
        assertNotNull(diagnostics);
        assertTrue(diagnostics.contains("expects 2 values"), diagnostics);
    }

    @Test
    public void testStructLiteralCoerceFails() {
        final String diagnostics = ZFixtures.failureOf("""
                struct Point { str name | int32 y }
                proc main() { Point p -> Point{1 | 2}
                 ret p.y }""");
        assertNotNull(diagnostics);
        assertTrue(diagnostics.contains("cannot convert between str"), diagnostics);
    }

    @Test
    public void testStructDeclOnlyModule() throws IOException {
        final String llvmIR = codeGen("struct Point { int32 x | int32 y }");
        assertNotNull(llvmIR);
        assertTrue(llvmIR.contains("%Point = type { i32, i32 }"), llvmIR);
    }

    @Test
    public void testStructAsGlobalVariable() throws IOException {
        final String llvmIR = codeGen("""
                struct Point { int32 x | int32 y }
                Point origin -> Point{0 | 0}
                proc main() { ret 0 }""");
        assertNotNull(llvmIR, "compilation failed");
        assertTrue(llvmIR.contains("@origin = global %Point { i32 0, i32 0 }"), llvmIR);
    }

    @Test
    public void testStructAsParameterAndReturn() throws IOException {
        final String llvmIR = codeGen("""
                struct Point { int32 x | int32 y }
                proc id(Point p) -> Point { ret p }
                proc main() {
                    Point p -> Point{1 | 2}
                    Point q -> id(p)
                    ret q.x
                }""");
        assertNotNull(llvmIR);
        assertTrue(llvmIR.contains("@id(%Point %p)"), llvmIR);
        assertTrue(llvmIR.contains("call %Point @id(%Point"), llvmIR);
    }

    @Test
    public void testFunctionReturningStructFallsOffFails() {
        final String diagnostics = ZFixtures.failureOf("""
                struct Point { int32 x | int32 y }
                proc f() -> Point { }""");
        assertNotNull(diagnostics);
        // No `ret`: FunctionAST reports that the function can reach the end
        // without returning.
        assertTrue(diagnostics.contains("without returning"), diagnostics);
    }

    @Test
    public void testStructInComparisonFails() {
        final String diagnostics =  ZFixtures.failureOf("""
                struct Point { int32 x | int32 y }
                proc main() {
                    Point p -> Point{1 | 2}
                    if (p < p) { ret 1 }
                    ret 0
                }""");
        assertNotNull(diagnostics);
        assertTrue(diagnostics.contains("struct cannot be used in a comparison"), diagnostics);
    }

    @Test
    public void testStructInArithmeticFails() {
        final String diagnostics = ZFixtures.failureOf("""
                struct Point { int32 x | int32 y }
                proc main() {
                    Point p -> Point{1 | 2}
                    ret p + p
                }""");
        assertNotNull(diagnostics);
        assertTrue(diagnostics.contains("struct cannot be used in a binary expression"), diagnostics);
    }

    @Test
    public void testStructComparedWithIntFails() {
        final String diagnostics = ZFixtures.failureOf("""
                struct Point { int32 x | int32 y }
                proc main() {
                    Point p -> Point{1 | 2}
                    ret p < 5
                }""");
        assertNotNull(diagnostics);
        assertTrue(diagnostics.contains("struct cannot be used in a binary expression"), diagnostics);
    }

    @Test
    public void testStructUnaryMinusFails() {
        final String diagnostics = ZFixtures.failureOf("""
                struct Point { int32 x | int32 y }
                proc main() {
                    Point p -> Point{1 | 2}
                    ret -p
                }""");
        assertNotNull(diagnostics);
        assertTrue(diagnostics.contains("cannot"), diagnostics);
    }

    @org.junit.jupiter.api.Disabled("Needs postfix .field on (expr) — follow-up PR")
    @Test
    public void testStructPointerFieldAccess() throws IOException {
        final String llvmIR = codeGen("""
                struct Point { int32 x | int32 y }
                proc main() {
                    Point p -> Point{1 | 2}
                    Point* pp -> &p
                    ret (*pp).x
                }""");
        assertNotNull(llvmIR);
        assertTrue(llvmIR.contains("getelementptr inbounds %Point"), llvmIR);
    }

    @Test
    public void testStructLiteralWithUndeclaredFieldValueFails() {
        final String diagnostics = ZFixtures.failureOf("""
                struct Point { int32 x | int32 y }
                proc main() {
                    Point p -> Point{ghost | 2}
                    ret 0
                }""");
        assertNotNull(diagnostics);
        assertTrue(diagnostics.contains("undeclared variable"), diagnostics);
    }

    @Test
    public void testStructLiteralWithWrongFieldTypeFails() {
        final String diagnostics = ZFixtures.failureOf("""
                struct Point { str name | int32 y }
                proc main() {
                    Point p -> Point{1 | 2}
                    ret 0
                }""");
        assertNotNull(diagnostics);
        assertTrue(diagnostics.contains("cannot convert between str"), diagnostics);
    }

    @Test
    public void testStructGlobalWithWrongArityFails() {
        final String diagnostics = ZFixtures.failureOf("""
                struct Point { int32 x | int32 y }
                Point origin -> Point{0}
                proc main() { ret 0 }""");
        assertNotNull(diagnostics);
        assertTrue(diagnostics.contains("constant expression"), diagnostics);
    }

    @Test
    public void testStructGlobalWithNonConstantFieldFails() {
        final String diagnostics = ZFixtures.failureOf("""
                struct Point { int32 x | int32 y }
                proc f() -> int32 { ret 5 }
                Point origin -> Point{f() | 0}
                proc main() { ret 0 }""");
        assertNotNull(diagnostics);
        assertTrue(diagnostics.contains("constant expression"), diagnostics);
    }

    @Test
    public void testVariableMissingNameFails() {
        final String diagnostics = ZFixtures.failureOf(
                "proc main() { int32\n ret 0 }");
        assertNotNull(diagnostics);
        assertTrue(diagnostics.contains("Expected name after type"), diagnostics);
    }

    @Test
    void testCallArgCodeGenFails() {
        final String diagnostics = ZFixtures.failureOf(
                "proc foo(int32 x) { ret x }\nproc main() { foo(ghost) }");
        assertNotNull(diagnostics);
        assertTrue(diagnostics.contains("undeclared variable"), diagnostics);
    }

    @Test
    public void testTwoConcatenationsEmitTheAllocatorOnce() throws IOException {
        final String llvmIR = codeGen("""
                proc main() {
                    str a -> "hello"
                    str b -> " world"
                    str c -> a + b
                    str d -> c + "!"
                    ret 0
                }""");

        assertNotNull(llvmIR);
        // The arena, its cursor, and the free list are emitted once.
        assertEquals(1, countOccurrences(llvmIR, "@.arena = global"), llvmIR);
        assertEquals(1, countOccurrences(llvmIR, "@.arena.used = global"), llvmIR);
        assertEquals(1, countOccurrences(llvmIR, "@.free_list = global"), llvmIR);
        // The two allocator functions are emitted once.
        assertEquals(1, countOccurrences(llvmIR, "define ptr @zalloc"), llvmIR);
        assertEquals(1, countOccurrences(llvmIR, "define void @zfree"), llvmIR);
        // Both concatenations call @zalloc.
        assertEquals(2, countOccurrences(llvmIR, "call ptr @zalloc(i64"), llvmIR);
    }

    @Test
    public void testAllocReturnsAPointer() throws IOException {
        final String llvmIR = codeGen("""
                proc main() {
                    ptr p -> alloc(64)
                    ret 0
                }""");

        assertNotNull(llvmIR);
        assertTrue(llvmIR.contains("call ptr @zalloc(i64 64)"), llvmIR);
    }

    @Test
    public void testFreeCallsZfree() throws IOException {
        final String llvmIR = codeGen("""
                proc main() {
                    ptr p -> alloc(64)
                    free(p)
                    ret 0
                }""");

        assertNotNull(llvmIR);
        assertTrue(llvmIR.contains("call ptr @zalloc(i64 64)"), llvmIR);
        assertTrue(llvmIR.contains("call void @zfree(ptr"), llvmIR);
    }

    @Test
    public void testAllocSizeIsAnExpression() throws IOException {
        final String llvmIR = codeGen("""
                proc main() {
                    int32 n -> 32
                    ptr p -> alloc(n * 2)
                    ret 0
                }""");

        assertNotNull(llvmIR);
        assertTrue(llvmIR.contains("call ptr @zalloc(i64"), llvmIR);
    }

    @Test
    public void testFreeingANonPointerFails() {
        final String diagnostics = ZFixtures.failureOf("""
                proc main() {
                    int32 x -> 1
                    free(x)
                    ret 0
                }""");

        assertNotNull(diagnostics);
        assertTrue(diagnostics.contains("free expects a pointer"), diagnostics);
    }

    @Test
    public void testFreeingAStringFreesTheDataPointer() throws IOException {
        final String llvmIR = codeGen("""
                proc main() {
                    str llvmTextUnit -> "hi"
                    free(llvmTextUnit)
                    ret 0
                }""");

        assertNotNull(llvmIR);
        assertTrue(llvmIR.contains("extractvalue { i8*, i64 }"), llvmIR);
        assertTrue(llvmIR.contains("call void @zfree(ptr"), llvmIR);
    }

    @Test
    public void testAllocatorIsEmittedOnce() throws IOException {
        final String llvmIR = codeGen("""
                proc main() {
                    ptr a -> alloc(8)
                    ptr b -> alloc(16)
                    free(a)
                    free(b)
                    ret 0
                }""");

        assertNotNull(llvmIR);
        assertEquals(1, countOccurrences(llvmIR, "define ptr @zalloc"), llvmIR);
        assertEquals(1, countOccurrences(llvmIR, "define void @zfree"), llvmIR);
        assertEquals(2, countOccurrences(llvmIR, "call ptr @zalloc"), llvmIR);
        assertEquals(2, countOccurrences(llvmIR, "call void @zfree"), llvmIR);
    }
}