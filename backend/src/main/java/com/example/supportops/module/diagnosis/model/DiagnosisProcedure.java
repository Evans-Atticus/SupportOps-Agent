package com.example.supportops.module.diagnosis.model;

import java.util.List;

/** 提供给前端展示的 SOP 聚合，包括标题、适用人员和有序操作步骤。 */
public record DiagnosisProcedure(String title, String audience, List<ProcedureInstruction> instructions) {
    public DiagnosisProcedure {
        // 防御性复制，防止 Controller 返回后调用方修改步骤集合。
        instructions = List.copyOf(instructions);
    }
}
