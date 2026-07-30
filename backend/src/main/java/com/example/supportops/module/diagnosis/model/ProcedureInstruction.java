package com.example.supportops.module.diagnosis.model;

/** SOP 中的一条可执行指令；ruleExpression 仅展示规则，不在运行时解释执行。 */
public record ProcedureInstruction(String order, String action, String tool, String text, String ruleExpression) {
}
