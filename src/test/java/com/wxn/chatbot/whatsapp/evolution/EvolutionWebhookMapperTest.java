package com.wxn.chatbot.whatsapp.evolution;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wxn.chatbot.whatsapp.model.NormalizedMessageStatus;
import org.junit.jupiter.api.Test;

class EvolutionWebhookMapperTest {

    private final EvolutionWebhookMapper mapper = new EvolutionWebhookMapper();
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void mapsIncomingTextMessage() throws Exception {
        var data = json.readTree(
            """
            {
              "key": {"remoteJid": "5511999999999@s.whatsapp.net", "id": "evo-1", "fromMe": false},
              "pushName": "Contato Real",
              "messageTimestamp": 1730000000,
              "message": {"conversation": "Olá WXN"}
            }
            """
        );

        var result = mapper.text(data, "+5511888888888");

        assertThat(result.externalMessageId()).isEqualTo("evo-1");
        assertThat(result.senderPhone()).isEqualTo("+5511999999999");
        assertThat(result.recipientPhone()).isEqualTo("+5511888888888");
        assertThat(result.content()).containsEntry("text", "Olá WXN");
        assertThat(result.senderDisplayName()).isEqualTo("Contato Real");
    }

    @Test
    void rejectsGroupsAndMapsEvolutionStatuses() throws Exception {
        var group = json.readTree(
            """
            {"key": {"remoteJid": "123@g.us"}, "message": {"conversation": "grupo"}}
            """
        );

        assertThat(mapper.supported(group)).isFalse();
        assertThat(mapper.status(json.readTree("2"))).isEqualTo(NormalizedMessageStatus.DELIVERED);
        assertThat(mapper.status(json.readTree("\"READ\""))).isEqualTo(NormalizedMessageStatus.READ);
        assertThat(mapper.status(json.readTree("\"ERROR\""))).isEqualTo(NormalizedMessageStatus.FAILED);
    }

    @Test
    void normalizesEvolutionEventNames() throws Exception {
        assertThat(
            EvolutionWebhookService.eventName(json.readTree("{\"event\":\"messages.upsert\"}"))
        ).isEqualTo("MESSAGES_UPSERT");
        assertThat(
            EvolutionWebhookService.eventName(json.readTree("{\"event\":\"MESSAGES_UPDATE\"}"))
        ).isEqualTo("MESSAGES_UPDATE");
    }

    @Test
    void prefersPhoneAliasWhenEvolutionUsesLid() throws Exception {
        var data = json.readTree(
            """
            {
              "key": {
                "remoteJid": "12345@lid",
                "remoteJidAlt": "5511999999999@s.whatsapp.net",
                "id": "evo-lid"
              },
              "message": {"conversation": "Olá"}
            }
            """
        );

        assertThat(mapper.text(data, "+5511888888888").senderPhone()).isEqualTo("+5511999999999");
    }

    @Test
    void extractsTextFromEditedProtocolMessage() throws Exception {
        var data = json.readTree(
            """
            {
              "message": {
                "protocolMessage": {
                  "key": {"id": "evo-1"},
                  "editedMessage": {"conversation": "Texto corrigido"}
                }
              },
              "messageTimestamp": 1730000000
            }
            """
        );

        assertThat(mapper.editedText(data)).isEqualTo("Texto corrigido");
    }

    @Test
    void extractsEditedMessageIdAndTextFromEvolutionUpdateEnvelope() throws Exception {
        var data = json.readTree(
            """
            {
              "key": {
                "remoteJid": "5511999999999@s.whatsapp.net",
                "id": "evo-edited"
              },
              "update": {
                "editedMessage": {"conversation": "Texto corrigido"}
              }
            }
            """
        );

        assertThat(mapper.messageId(data)).isEqualTo("evo-edited");
        assertThat(mapper.remotePhone(data)).isEqualTo("+5511999999999");
        assertThat(mapper.editedText(data)).isEqualTo("Texto corrigido");
    }
}
