package com.wxn.chatbot.demo.service;

import com.wxn.chatbot.conversation.document.Conversation;
import com.wxn.chatbot.conversation.mapper.ConversationMapper;
import com.wxn.chatbot.message.document.Message;
import com.wxn.chatbot.message.dto.MessageDtos;
import com.wxn.chatbot.message.mapper.MessageMapper;
import com.wxn.chatbot.user.dto.UserDtos;
import com.wxn.chatbot.whatsapp.entity.WhatsAppChannel;
import com.wxn.chatbot.whatsapp.model.*;
import java.time.*;
import java.util.*;
import org.bson.Document;

public final class DemoFixtures {

    private DemoFixtures() {}

    private static final String[] NAMES = {
        "Alice",
        "Bruno",
        "Clara",
        "Davi",
        "Elisa",
        "Felipe",
        "Gabriela",
        "Hugo",
        "Isabela",
        "Júlio",
    };

    public static String cpf(int index) {
        String base = String.format(java.util.Locale.ROOT, "682%06d", 100 + index);
        String result = base;
        for (int size = 9; size <= 10; size++) {
            int sum = 0;
            for (int i = 0; i < size; i++) {
                sum += (result.charAt(i) - '0') * (size + 1 - i);
            }
            int digit = 11 - (sum % 11);
            result += digit >= 10 ? 0 : digit;
        }
        return result;
    }

    public static String phone(int contact) {
        return "+1202555" + String.format(java.util.Locale.ROOT, "%04d", 100 + contact);
    }

    public static String name(int contact) {
        return (
            NAMES[contact % NAMES.length] +
            " Demo " +
            String.format(java.util.Locale.ROOT, "%02d", contact + 1)
        );
    }

    public static String status(int index) {
        return index < 15
            ? "PENDING"
            : index < 40
              ? "IN_PROGRESS"
              : index < 52
                ? "WAITING_CUSTOMER"
                : index < 72
                  ? "RESOLVED"
                  : "CLOSED";
    }

    public static Conversation conversation(
        int index,
        WhatsAppChannel channel,
        UserDtos.Response owner,
        Long department,
        Instant now
    ) {
        String phone = phone(index % 40);
        Conversation c = ConversationMapper.individual(channel, phone, "individual:" + phone);
        String status = status(index);
        boolean finished = Set.of("RESOLVED", "CLOSED").contains(status);
        OffsetDateTime created = now
            .minusSeconds(finished ? ((index % 18) + 1) * 86400L : (10 + index * 4L) * 60)
            .atOffset(ZoneOffset.UTC);
        c.setDisplayName(name(index % 40));
        c.setStatus(status);
        c.setDepartmentId(department);
        c.setAssignedMemberId("PENDING".equals(status) ? null : owner.memberId());
        c.setPriority(
            index % 11 == 0 ? "URGENT" : index % 5 == 0 ? "HIGH" : index % 7 == 0 ? "LOW" : "NORMAL"
        );
        c.setCreatedAt(created);
        c.setCycleStartedAt(created);
        c.setUpdatedAt(created.plusMinutes(finished ? 45 + index : 5));
        if (finished) {
            c.setResolvedAt(c.getUpdatedAt());
            if ("CLOSED".equals(status)) {
                c.setClosedAt(c.getUpdatedAt());
            }
        }
        if ("IN_PROGRESS".equals(status) || "PENDING".equals(status)) {
            c.setWaitingForAgentSince(
                now
                    .minusSeconds((index % 3 == 0 ? 45 : index % 3 == 1 ? 26 : 4) * 60L)
                    .atOffset(ZoneOffset.UTC)
            );
            if (c.getWaitingForAgentSince().isBefore(created)) {
                c.setWaitingForAgentSince(created);
            }
        }
        if ("PENDING".equals(status)) {
            c.setWaitingForAgentSince(created);
        }
        OffsetDateTime end =
            c.getWaitingForAgentSince() != null
                ? c.getWaitingForAgentSince().plusSeconds(30)
                : c.getUpdatedAt();
        c.setUpdatedAt(end);
        c.setLastMessageAt(end);
        c.setLastMessagePreview(
            c.getWaitingForAgentSince() != null
                ? "Obrigado. Aguardo o retorno da equipe."
                : "Combinado! O próximo passo foi registrado. Este diálogo é apenas uma demonstração."
        );
        long span = Math.max(0, Duration.between(created, end).getSeconds());
        c.setLastInboundAt(c.getWaitingForAgentSince() != null ? end : created.plusSeconds((span * 4) / 5));
        if (!"PENDING".equals(status)) {
            c.setLastOutboundAt(
                c.getWaitingForAgentSince() != null
                    ? created.plusSeconds(
                          (Math.max(
                              0,
                              Duration.between(
                                  created,
                                  c.getWaitingForAgentSince().minusSeconds(30)
                              ).getSeconds()
                          ) *
                              3) /
                              4
                      )
                    : end
            );
        }
        List<Document> cycles = new ArrayList<>();
        if (index == 25 || index == 65) {
            c.setCreatedAt(created.minusDays(2));
            c.setReopenedAt(created);
            c.setReopenCount(1);
            cycles.add(
                new Document("startedAt", Date.from(created.minusDays(2).toInstant()))
                    .append("resolvedAt", Date.from(created.minusDays(2).plusMinutes(42).toInstant()))
                    .append("memberId", owner.memberId())
            );
        }
        if (finished) {
            cycles.add(
                new Document("startedAt", Date.from(created.toInstant()))
                    .append("resolvedAt", Date.from(c.getResolvedAt().toInstant()))
                    .append("memberId", owner.memberId())
            );
        }
        c.setResolutionCycles(cycles);
        return c;
    }

    public static Message message(
        int conversationIndex,
        int index,
        WhatsAppChannel channel,
        Conversation c,
        UserDtos.Response owner
    ) {
        boolean inbound =
            "PENDING".equals(c.getStatus()) ||
            index % 2 == 0 ||
            (index == 5 && c.getWaitingForAgentSince() != null);
        Instant start = c.getCycleStartedAt().toInstant();
        Instant end = c.getLastMessageAt().toInstant();
        long span = Math.max(0, Duration.between(start, end).toMillis());
        Instant at;
        if (c.getWaitingForAgentSince() != null && !"PENDING".equals(c.getStatus())) {
            Instant waiting = c.getWaitingForAgentSince().toInstant();
            long before = Math.max(0, Duration.between(start, waiting.minusSeconds(30)).toMillis());
            at = index < 4 ? start.plusMillis((before * index) / 3) : index == 4 ? waiting : end;
        } else {
            at = start.plusMillis((span * index) / 5);
        }
        String[] inboundText = {
            "Olá! Este é um atendimento fictício para demonstração. Gostaria de um orçamento.",
            "Tenho interesse na solução de atendimento. Podem explicar as opções?",
            "Obrigado. Aguardo o retorno da equipe.",
        };
        String[] outboundText = {
            "Olá, @@nome!".replace("@@nome", c.getDisplayName()) +
                " Sou da equipe WXN Demo. Como posso ajudar?",
            "Podemos preparar uma proposta fictícia para o seu cenário.",
            "Combinado! O próximo passo foi registrado. Este diálogo é apenas uma demonstração.",
        };
        String text = inbound ? inboundText[Math.min(2, index / 2)] : outboundText[Math.min(2, index / 2)];
        String external = "wxn-demo-v1-c" + conversationIndex + "-m" + index;
        Message m = inbound
            ? MessageMapper.inbound(
                  channel,
                  c,
                  new NormalizedInboundMessage(
                      external,
                      c.getContactPhone(),
                      channel.getPhoneNumber(),
                      MessageType.TEXT,
                      Map.of("text", text),
                      at,
                      c.getDisplayName()
                  ),
                  true
              )
            : MessageMapper.outbound(
                  channel,
                  c,
                  new MessageDtos.Send(channel.getId(), owner.memberId(), text, external, owner.id()),
                  c.getContactPhone(),
                  true
              );
        m.setExternalMessageId(external);
        m.setCreatedAt(at.atOffset(ZoneOffset.UTC));
        m.setUpdatedAt(m.getCreatedAt());
        m.setInboundCompleted(true);
        m.setStatus(
            inbound
                ? NormalizedMessageStatus.RECEIVED
                : conversationIndex % 13 == 0 && index == 3
                  ? NormalizedMessageStatus.FAILED
                  : index == 5
                    ? NormalizedMessageStatus.READ
                    : index == 3
                      ? NormalizedMessageStatus.DELIVERED
                      : NormalizedMessageStatus.SENT
        );
        if (m.getStatus() == NormalizedMessageStatus.FAILED) {
            m.setErrorCode("MOCK_DEMO_FAILURE");
        }
        return m;
    }
}
