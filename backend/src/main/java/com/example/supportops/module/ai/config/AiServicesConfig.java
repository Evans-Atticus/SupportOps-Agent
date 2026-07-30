package com.example.supportops.module.ai.config;

import com.example.supportops.module.ai.reply.CustomerReplyAiService;
import com.example.supportops.module.ai.understanding.TicketUnderstandingAiService;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** real 模式显式构建两个 AI Service，业务层始终只依赖接口。 */
@Configuration
@ConditionalOnProperty(name = "supportops.ai.mode", havingValue = "real")
public class AiServicesConfig {

    @Bean
    TicketUnderstandingAiService ticketUnderstandingAiService(ChatModel chatModel) {
        return AiServices.builder(TicketUnderstandingAiService.class).chatModel(chatModel).build();
    }

    @Bean
    CustomerReplyAiService customerReplyAiService(ChatModel chatModel) {
        return AiServices.builder(CustomerReplyAiService.class).chatModel(chatModel).build();
    }
}
