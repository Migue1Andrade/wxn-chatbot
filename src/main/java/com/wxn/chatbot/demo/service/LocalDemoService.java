package com.wxn.chatbot.demo.service;

import com.wxn.chatbot.auth.service.AccountSessions;
import com.wxn.chatbot.collaboration.document.TeamEntry;
import com.wxn.chatbot.common.*;
import com.wxn.chatbot.conversation.document.*;
import com.wxn.chatbot.conversation.model.ConversationActivityType;
import com.wxn.chatbot.demo.repository.DemoSeedStore;
import com.wxn.chatbot.department.dto.DepartmentDtos;
import com.wxn.chatbot.department.service.DepartmentService;
import com.wxn.chatbot.kanban.entity.KanbanView;
import com.wxn.chatbot.kanban.repository.KanbanViewRepository;
import com.wxn.chatbot.message.document.Message;
import com.wxn.chatbot.notification.entity.UserNotification;
import com.wxn.chatbot.notification.repository.NotificationRepository;
import com.wxn.chatbot.quickreply.entity.QuickReply;
import com.wxn.chatbot.quickreply.repository.QuickReplyRepository;
import com.wxn.chatbot.task.entity.WorkTask;
import com.wxn.chatbot.task.repository.WorkTaskRepository;
import com.wxn.chatbot.user.dto.UserDtos;
import com.wxn.chatbot.user.entity.UserRole;
import com.wxn.chatbot.user.repository.UserRepository;
import com.wxn.chatbot.user.service.UserService;
import com.wxn.chatbot.whatsapp.dto.ChannelDtos;
import com.wxn.chatbot.whatsapp.model.*;
import com.wxn.chatbot.whatsapp.service.ChannelService;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import org.bson.Document;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("dev & !prod")
public class LocalDemoService {

    public record Count(int created, int existing) {}

    public record Report(
        String namespace,
        Map<String, Count> resources,
        Map<String, Long> postgresBefore,
        Map<String, Long> mongoBefore,
        Map<String, Long> postgres,
        Map<String, Long> mongo,
        List<String> warnings,
        List<String> failures
    ) {}

    private final DemoSeedStore store;
    private final UserService users;
    private final UserRepository userRepository;
    private final DepartmentService departments;
    private final ChannelService channels;
    private final QuickReplyRepository replies;
    private final WorkTaskRepository tasks;
    private final NotificationRepository notifications;
    private final KanbanViewRepository views;
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(LocalDemoService.class);
    private String currentResource;
    private final Map<String, Count> counts = new LinkedHashMap<>();

    public LocalDemoService(
        DemoSeedStore store,
        UserService users,
        UserRepository userRepository,
        DepartmentService departments,
        ChannelService channels,
        QuickReplyRepository replies,
        WorkTaskRepository tasks,
        NotificationRepository notifications,
        KanbanViewRepository views
    ) {
        this.store = store;
        this.users = users;
        this.userRepository = userRepository;
        this.departments = departments;
        this.channels = channels;
        this.replies = replies;
        this.tasks = tasks;
        this.notifications = notifications;
        this.views = views;
    }

    private String key(String key) {
        return "wxn-demo-v1:" + key;
    }

    private void count(String type, boolean created) {
        var old = counts.getOrDefault(type, new Count(0, 0));
        counts.put(type, new Count(old.created() + (created ? 1 : 0), old.existing() + (created ? 0 : 1)));
    }

    private Long relational(String key, String type, Supplier<String> factory) {
        currentResource = type + "/" + key;
        var result = store.relational(key(key), type, factory);
        count(type, result.created());
        return Long.valueOf(result.value());
    }

    private <T> T document(String key, String type, Class<T> model, Supplier<T> factory) {
        currentResource = type + "/" + key;
        var result = store.document(key(key), type, model, factory);
        count(type, result.created());
        return result.value();
    }

    public synchronized Report run(String password, AccountSessions.Identity actor) {
        store.preflight();
        var postgresBefore = store.postgresCounts();
        var mongoBefore = store.mongoCounts();
        counts.clear();
        List<String> warnings = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        warnings.add(
            "Configurações e senhas existentes são preservadas. SLA usa a política atual; se estiver desabilitado, habilite-o manualmente para visualizar riscos."
        );
        warnings.add(
            "Contatos independentes, etiquetas, menções, solicitações de transferência e handoff não existem no domínio atual e não foram simulados como recursos reais."
        );
        try {
            String[] names = {
                "Atendimento Geral",
                "Comercial",
                "Suporte Técnico",
                "Financeiro",
                "Pós-venda",
                "Implantação",
            };
            String[] colors = { "#187566", "#356AA2", "#7360A7", "#A67832", "#A65D63", "#547D91" };
            List<Long> departmentIds = new ArrayList<>();
            for (int i = 0; i < names.length; i++) {
                final int n = i;
                departmentIds.add(
                    relational("department-" + i, "departments", () ->
                        departments
                            .create(
                                new DepartmentDtos.Save(
                                    names[n] + " [Demo]",
                                    colors[n],
                                    "Setor fictício para demonstração local",
                                    ActiveStatus.ACTIVE,
                                    null
                                )
                            )
                            .id()
                            .toString()
                    )
                );
            }
            for (Long id : departmentIds) {
                if (departments.require(id).getStatus() != ActiveStatus.ACTIVE) {
                    throw ApiException.conflict("Setor demo foi inativado; preservado sem novas atribuições");
                }
            }
            List<UserDtos.Response> people = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                final int n = i;
                String username = "wxn.demo." + (i == 0 ? "admin" : i == 7 ? "inactive" : "attendant" + i);
                Long id = relational("user-" + i, "users", () -> {
                    String email = username + "@example.com";
                    String cpf = DemoFixtures.cpf(n);
                    if (
                        userRepository.findByUsernameIgnoreCase(username).isPresent() ||
                        userRepository.findByEmailIgnoreCase(email).isPresent() ||
                        userRepository.findByCpf(cpf).isPresent()
                    ) {
                        throw ApiException.conflict(
                            "Conflito de identidade demo: " + username + "; conta existente preservada"
                        );
                    }
                    return users
                        .create(
                            new UserDtos.Create(
                                n == 0
                                    ? "Administrador Demo"
                                    : n == 7
                                      ? "Atendente Inativo Demo"
                                      : "Atendente Demo " + n,
                                username,
                                email,
                                cpf,
                                null,
                                password,
                                password,
                                n == 0 ? UserRole.ADMIN : UserRole.ATTENDANT,
                                n == 7 ? ActiveStatus.INACTIVE : ActiveStatus.ACTIVE,
                                n == 0
                                    ? List.of()
                                    : List.of(departmentIds.get((n - 1) % 6), departmentIds.get(n % 6))
                            )
                        )
                        .id()
                        .toString();
                });
                var person = users.get(id);
                if (
                    i < 7 &&
                    (person.accountStatus() != ActiveStatus.ACTIVE ||
                        person.memberId() == null ||
                        person.role() != (i == 0 ? UserRole.ADMIN : UserRole.ATTENDANT))
                ) {
                    throw ApiException.conflict(
                        "Conta demo alterada/inativa; preservada sem novas atribuições"
                    );
                }
                people.add(person);
            }
            List<com.wxn.chatbot.whatsapp.entity.WhatsAppChannel> demoChannels = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                final int n = i;
                Long id = relational("channel-" + i, "whatsapp_channels", () ->
                    channels
                        .create(
                            new ChannelDtos.Create(
                                "WhatsApp " +
                                    List.of("Principal", "Comercial", "Suporte").get(n) +
                                    " — Mock [Demo]",
                                ProviderType.MOCK,
                                DemoFixtures.phone(90 + n),
                                key("channel-" + n),
                                ActiveStatus.ACTIVE
                            )
                        )
                        .id()
                        .toString()
                );
                var channel = channels.requireActive(id);
                if (channel.getProvider() != ProviderType.MOCK) {
                    throw ApiException.conflict("Canal demo deixou de ser MOCK; seed interrompido");
                }
                demoChannels.add(channel);
            }
            Instant now = Instant.now();
            List<Conversation> conversations = new ArrayList<>();
            for (int i = 0; i < 80; i++) {
                final int n = i;
                var channel = demoChannels.get(i % 3);
                var owner = people.get(i == 15 ? 0 : 1 + (i % 6));
                Conversation c = document("conversation-" + i, "conversations", Conversation.class, () ->
                    DemoFixtures.conversation(n, channel, owner, departmentIds.get(n % 6), now)
                );
                conversations.add(c);
                if (
                    c.getProvider() != ProviderType.MOCK || !Objects.equals(c.getChannelId(), channel.getId())
                ) {
                    throw ApiException.conflict("Conversa demo foi alterada para outro canal; preservada");
                }
                org.bson.types.ObjectId lastMessageId = null;
                for (int j = 0; j < 6; j++) {
                    final int m = j;
                    Message message = document(
                        "conversation-" + i + "-message-" + j,
                        "messages",
                        Message.class,
                        () -> DemoFixtures.message(n, m, channel, c, owner)
                    );
                    lastMessageId = message.getId();
                    document("log-" + i + "-" + j, "message_logs", Document.class, () ->
                        new Document("messageId", message.getId())
                            .append("channelId", channel.getId())
                            .append("eventKey", key("log-" + n + "-" + m))
                            .append(
                                "event",
                                "INBOUND".equals(message.getDirection())
                                    ? "message.received"
                                    : message.getStatus() == NormalizedMessageStatus.FAILED
                                      ? "message.send.failed"
                                      : "message.sent"
                            )
                            .append("status", message.getStatus().name())
                            .append("createdAt", Date.from(message.getCreatedAt().toInstant()))
                    );
                }
                store.linkLastMessage(key("conversation-" + i), lastMessageId);
                final org.bson.types.ObjectId readThrough = lastMessageId;
                document("event-created-" + i, "conversation_events", ConversationActivity.class, () ->
                    new ConversationActivity(
                        c.getId(),
                        key("event-created-" + n),
                        ConversationActivityType.CONVERSATION_CREATED,
                        null,
                        null,
                        null,
                        "Atendimento fictício importado pelo seed local",
                        c.getCreatedAt()
                    )
                );
                if (!"PENDING".equals(c.getStatus())) {
                    document("event-assigned-" + i, "conversation_events", ConversationActivity.class, () ->
                        new ConversationActivity(
                            c.getId(),
                            key("event-assigned-" + n),
                            ConversationActivityType.MEMBER_ASSIGNED,
                            people.get(0).memberId(),
                            null,
                            n == 29 ? people.get(1).memberId() : owner.memberId(),
                            "Atribuição fictícia de demonstração",
                            c.getCycleStartedAt()
                        )
                    );
                }
                if (i == 29) {
                    document("transfer-29-first", "conversation_events", ConversationActivity.class, () ->
                        new ConversationActivity(
                            c.getId(),
                            key("transfer-29-first"),
                            ConversationActivityType.MEMBER_TRANSFERRED,
                            people.get(0).memberId(),
                            people.get(1).memberId(),
                            people.get(2).memberId(),
                            "Transferência fictícia entre setores",
                            c.getCycleStartedAt().plusMinutes(1)
                        )
                    );
                    document("transfer-29-final", "conversation_events", ConversationActivity.class, () ->
                        new ConversationActivity(
                            c.getId(),
                            key("transfer-29-final"),
                            ConversationActivityType.MEMBER_TRANSFERRED,
                            people.get(0).memberId(),
                            people.get(2).memberId(),
                            owner.memberId(),
                            "Responsável final da demonstração",
                            c.getCycleStartedAt().plusMinutes(2)
                        )
                    );
                }
                if (i == 25 || i == 65) {
                    document(
                        "previous-resolution-" + i,
                        "conversation_events",
                        ConversationActivity.class,
                        () ->
                            new ConversationActivity(
                                c.getId(),
                                key("previous-resolution-" + n),
                                ConversationActivityType.CONVERSATION_RESOLVED,
                                owner.memberId(),
                                owner.memberId(),
                                owner.memberId(),
                                "Resolução do primeiro ciclo de demonstração",
                                c.getCreatedAt().plusMinutes(42)
                            )
                    );
                    document("reopened-" + i, "conversation_events", ConversationActivity.class, () ->
                        new ConversationActivity(
                            c.getId(),
                            key("reopened-" + n),
                            ConversationActivityType.CONVERSATION_REOPENED,
                            owner.memberId(),
                            owner.memberId(),
                            owner.memberId(),
                            "Reabertura fictícia; ciclo anterior preservado",
                            c.getCycleStartedAt()
                        )
                    );
                }
                if (i % 4 == 0 && c.getAssignedMemberId() != null) {
                    document("read-" + i, "conversation_reads", Document.class, () ->
                        new Document("conversationId", c.getId())
                            .append("memberId", owner.memberId())
                            .append("throughMessageId", readThrough)
                            .append("lastReadAt", Date.from(c.getLastMessageAt().toInstant()))
                    );
                }
                if (i == 19 || i == 20) {
                    document("draft-" + i, "conversation_drafts", Document.class, () ->
                        new Document("conversationId", c.getId())
                            .append("userId", owner.id())
                            .append("mode", n == 19 ? "EXTERNAL" : "INTERNAL")
                            .append("content", "Rascunho fictício preservado para demonstração local.")
                            .append("updatedAt", Date.from(now))
                    );
                }
                if (i % 2 == 0) {
                    team(c, owner, n, "NOTE", now);
                }
                if (i % 3 == 0) {
                    team(c, owner, n, "INTERNAL", now);
                }
                if (i >= 52) {
                    document("event-resolved-" + i, "conversation_events", ConversationActivity.class, () ->
                        new ConversationActivity(
                            c.getId(),
                            key("event-resolved-" + n),
                            ConversationActivityType.CONVERSATION_RESOLVED,
                            owner.memberId(),
                            owner.memberId(),
                            owner.memberId(),
                            "Conclusão fictícia",
                            c.getResolvedAt()
                        )
                    );
                }
                if (i >= 72) {
                    document("event-closed-" + i, "conversation_events", ConversationActivity.class, () ->
                        new ConversationActivity(
                            c.getId(),
                            key("event-closed-" + n),
                            ConversationActivityType.CONVERSATION_CLOSED,
                            owner.memberId(),
                            owner.memberId(),
                            owner.memberId(),
                            "Encerramento fictício de demonstração",
                            c.getClosedAt()
                        )
                    );
                }
            }
            templates(people);
            for (int i = 0; i < 32; i++) {
                final int n = i;
                var c = conversations.get(15 + (i % 25));
                var owner = people
                    .stream()
                    .filter(person -> java.util.Objects.equals(person.memberId(), c.getAssignedMemberId()))
                    .findFirst()
                    .orElseThrow();
                Long id = relational("task-" + i, "work_tasks", () -> {
                    var task = new WorkTask(people.get(0).id(), c.getId().toHexString());
                    task.update(
                        List.of("TASK", "FOLLOW_UP", "REMINDER").get(n % 3),
                        List.of(
                            "Preparar proposta",
                            "Retornar ao cliente",
                            "Validar cenário técnico",
                            "Confirmar implantação"
                        ).get(n % 4) +
                            " [Demo " +
                            (n + 1) +
                            "]",
                        "Atividade fictícia associada a " + c.getDisplayName(),
                        owner.id(),
                        now.plusSeconds(n % 4 == 0 ? -3600 : 3600L * ((n % 8) + 1)),
                        n % 5 == 0 ? "COMPLETED" : n % 7 == 0 ? "CANCELLED" : "OPEN",
                        n % 6 == 0 ? "HIGH" : "NORMAL"
                    );
                    return tasks.saveAndFlush(task).getId().toString();
                });
                relational("notification-task-" + i, "user_notifications", () -> {
                    var task = tasks.findById(id).orElseThrow();
                    boolean overdue = "OPEN".equals(task.getStatus()) && task.getDueAt().isBefore(now);
                    var note = new UserNotification(
                        owner.id(),
                        overdue ? "TASK_OVERDUE" : "TASK_ASSIGNED",
                        overdue ? "Atividade de demonstração vencida" : "Atividade de demonstração atribuída",
                        "TASK",
                        id.toString(),
                        now.minusSeconds(n * 3600L)
                    );
                    if (overdue) {
                        task.notified();
                    }
                    if (n % 3 == 0) {
                        note.read();
                    }
                    return notifications.saveAndFlush(note).getId().toString();
                });
            }
            for (int i = 0; i < 3; i++) {
                final int n = i;
                relational("view-" + i, "kanban_views", () ->
                    views
                        .saveAndFlush(
                            new KanbanView(
                                people.get(0).id(),
                                List.of("Demo · Sem responsável", "Demo · Urgentes", "Demo · Recentes").get(
                                    n
                                ),
                                List.of(
                                    "{\"filters\":{\"unassigned\":true,\"sort\":\"ATTENTION\"},\"layout\":\"board\"}",
                                    "{\"filters\":{\"priority\":\"URGENT\",\"sort\":\"ATTENTION\"},\"layout\":\"board\"}",
                                    "{\"filters\":{\"sort\":\"RECENT\"},\"layout\":\"list\"}"
                                ).get(n)
                            )
                        )
                        .getId()
                        .toString()
                );
            }
        } catch (ApiException conflict) {
            failures.add(conflict.getMessage());
        } catch (RuntimeException failed) {
            LOG.error(
                "Seed demo interrompido em {} ({})",
                currentResource,
                failed.getClass().getSimpleName()
            );
            failures.add(
                "Seed interrompido por falha de persistência. Nenhum registro existente foi sobrescrito; consulte os logs locais e execute novamente após corrigir a causa."
            );
        }
        return new Report(
            "wxn-demo-v1",
            Map.copyOf(counts),
            postgresBefore,
            mongoBefore,
            store.postgresCounts(),
            store.mongoCounts(),
            warnings,
            failures
        );
    }

    private void team(Conversation c, UserDtos.Response owner, int index, String kind, Instant now) {
        String key = kind.toLowerCase(java.util.Locale.ROOT) + "-" + index;
        Instant at =
            c.getResolvedAt() != null ? c.getResolvedAt().minusMinutes(1).toInstant() : now.minusSeconds(60);
        document(key, "team_entries", TeamEntry.class, () ->
            new TeamEntry(
                null,
                c.getId(),
                owner.id(),
                owner.name(),
                kind,
                "NOTE".equals(kind)
                    ? "Cliente fictício solicitou retorno. Validar proposta com o setor responsável."
                    : "Discussão interna de demonstração: cenário validado pela equipe. Não enviar ao cliente.",
                index % 4 == 0,
                at,
                at,
                null,
                0,
                List.of(),
                "wxn-demo-" + key
            )
        );
    }

    private void templates(List<UserDtos.Response> people) {
        String[] shortcuts = {
            "boas_vindas",
            "despedida",
            "horario",
            "aguarde",
            "transferencia",
            "orcamento",
            "suporte",
        };
        String[] content = {
            "Olá, @@primeiro_nome! Sou @@atendente. Como posso ajudar?",
            "Obrigado pelo contato, @@nome. Conte conosco!",
            "@@nome, vamos confirmar o horário disponível para seu atendimento.",
            "Um momento, @@nome. Estou verificando sua solicitação.",
            "Vou direcionar seu atendimento ao setor responsável.",
            "@@nome, vamos preparar um orçamento para sua necessidade.",
            "Olá, @@nome. Pode descrever o problema que encontrou?",
        };
        for (int i = 0; i < shortcuts.length; i++) {
            final int n = i;
            var existing = replies.findByScopeAndShortcutAndDeletedAtIsNull("GLOBAL", shortcuts[n]);
            if (existing.isPresent()) {
                store.reference(
                    key("template-global-" + i),
                    "quick_replies",
                    existing.get().getId().toString()
                );
                count("quick_replies", false);
            } else {
                relational("template-global-" + i, "quick_replies", () -> {
                    var q = new QuickReply("GLOBAL", null);
                    q.update(
                        "Demo · " + shortcuts[n],
                        shortcuts[n],
                        "Exemplo local de resposta rápida",
                        content[n],
                        true
                    );
                    return replies.saveAndFlush(q).getId().toString();
                });
            }
        }
        for (int i = 1; i <= 3; i++) {
            final int n = i;
            relational("template-personal-" + i, "quick_replies", () -> {
                var q = new QuickReply("PERSONAL", people.get(n).id());
                q.update(
                    List.of("Minha apresentação Demo", "Retorno Demo", "Proposta Demo").get(n - 1),
                    List.of("minha_apresentacao", "retorno", "proposta").get(n - 1),
                    "Resposta pessoal de demonstração",
                    "Olá, @@nome! Sou @@atendente e vou acompanhar seu atendimento @@protocolo.",
                    true
                );
                return replies.saveAndFlush(q).getId().toString();
            });
        }
    }
}
