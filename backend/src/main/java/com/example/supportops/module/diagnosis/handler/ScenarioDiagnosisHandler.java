package com.example.supportops.module.diagnosis.handler;

import com.example.supportops.module.diagnosis.model.DiagnosisContext;
import com.example.supportops.module.diagnosis.model.DiagnosisResult;
import com.example.supportops.module.diagnosis.model.enums.ScenarioType;

/** Strategy 接口：每个实现只负责一个场景的业务判断。 */
public interface ScenarioDiagnosisHandler {
    /** 返回本策略唯一支持的场景，注册表以此作为索引键。 */
    ScenarioType supports();

    /** 使用只读业务快照生成确定性结论，不执行数据库查询或外部调用。 */
    DiagnosisResult diagnose(DiagnosisContext context);
}
