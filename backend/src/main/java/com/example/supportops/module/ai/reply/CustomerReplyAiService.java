package com.example.supportops.module.ai.reply;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/** LangChain4j AI Service：仅依据已验证、已脱敏的诊断上下文润色客服回复。 */
public interface CustomerReplyAiService {

    @SystemMessage(fromResource = "prompts/customer-reply.txt")
    ReplyDraft generate(@UserMessage String verifiedDiagnosisContext);
}
