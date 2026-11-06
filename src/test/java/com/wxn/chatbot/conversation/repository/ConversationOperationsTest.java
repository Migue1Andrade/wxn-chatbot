package com.wxn.chatbot.conversation.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.wxn.chatbot.conversation.document.Conversation;
import java.util.List;
import java.util.regex.Pattern;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

class ConversationOperationsTest {

    private Document searchCriteria(String term) {
        MongoTemplate mongo = mock(MongoTemplate.class);
        when(mongo.find(any(Query.class), eq(Conversation.class))).thenReturn(List.of());
        new ConversationOperationsImpl(mongo).search(null, null, false, 0, 30, term, true);
        var query = ArgumentCaptor.forClass(Query.class);
        verify(mongo).find(query.capture(), eq(Conversation.class));
        return query.getValue().getQueryObject();
    }

    @Test
    void punctuationOnlySearchOmitsEmptyPhoneRegexAndKeepsLiteralSearch() {
        for (String term : List.of("()", "-", " ( - ) ")) {
            List<Document> conditions = searchCriteria(term).getList("$and", Document.class);
            List<Document> identities = conditions.get(0).getList("$or", Document.class);
            assertThat(identities).noneMatch(identity -> identity.containsKey("contactPhone"));
            Pattern name = identities.get(0).get("displayName", Pattern.class);
            assertThat(name.matcher("Equipe " + term.trim()).find()).isTrue();
            assertThat(name.matcher("Ana").find()).isFalse();
        }
    }

    @Test
    void formattedPhoneStillProducesNormalizedPhoneSearch() {
        List<Document> conditions = searchCriteria("+55 (81) 99999-9999").getList("$and", Document.class);
        List<Document> identities = conditions.get(0).getList("$or", Document.class);
        Pattern phone = identities
            .stream()
            .filter(row -> row.containsKey("contactPhone"))
            .findFirst()
            .orElseThrow()
            .get("contactPhone", Pattern.class);
        assertThat(phone.matcher("+5581999999999").find()).isTrue();
        assertThat(phone.matcher("+5511888888888").find()).isFalse();
    }
}
