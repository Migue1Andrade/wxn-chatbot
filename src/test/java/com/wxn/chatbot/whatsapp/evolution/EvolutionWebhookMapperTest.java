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
}
