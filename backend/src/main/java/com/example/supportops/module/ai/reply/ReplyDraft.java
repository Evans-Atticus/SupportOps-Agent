package com.example.supportops.module.ai.reply;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 客服回复的结构化结果，内容长度在进入数据库前再次校验。 */
public record ReplyDraft(
        @NotBlank @Size(max = 1000) String content,
        @NotBlank String tone
) {
}
