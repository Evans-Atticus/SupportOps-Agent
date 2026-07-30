package com.example.supportops.module.ai.understanding;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

/** LangChain4j AI Service：只负责工单分类和实体抽取。 */
public interface TicketUnderstandingAiService {

    @SystemMessage(fromResource = "prompts/ticket-understanding.txt")
    TicketIntent understand(@UserMessage String customerDescription);
}
