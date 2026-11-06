package com.wxn.chatbot.kanban.service;

import com.wxn.chatbot.common.ApiException;
import com.wxn.chatbot.kanban.dto.KanbanDtos.*;
import com.wxn.chatbot.kanban.entity.KanbanView;
import com.wxn.chatbot.kanban.repository.KanbanViewRepository;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class KanbanViewService {

    private final KanbanViewRepository repository;
    private final com.fasterxml.jackson.databind.ObjectMapper json;

    public KanbanViewService(
        KanbanViewRepository repository,
        com.fasterxml.jackson.databind.ObjectMapper json
    ) {
        this.repository = repository;
        this.json = json;
    }

    private View response(KanbanView view) {
        return new View(view.getId(), view.getName(), view.getConfiguration());
    }

    public List<View> list(Long owner) {
        return repository.findByOwnerUserIdOrderByName(owner).stream().map(this::response).toList();
    }

    private KanbanView require(Long id, Long owner) {
        return repository
            .findById(id)
            .filter(v -> Objects.equals(owner, v.getOwnerUserId()))
            .orElseThrow(() -> ApiException.notFound("Visualização não encontrada"));
    }

    public View save(Long id, Long owner, ViewInput input) {
        try {
            if (!json.readTree(input.configuration()).isObject()) {
                throw ApiException.badRequest("Configuração inválida");
            }
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw ApiException.badRequest("Configuração JSON inválida");
        }
        if (id == null && repository.findByOwnerUserIdOrderByName(owner).size() >= 50) {
            throw ApiException.conflict("Limite de 50 visualizações atingido");
        }
        var view =
            id == null ? new KanbanView(owner, input.name(), input.configuration()) : require(id, owner);
        view.update(input.name(), input.configuration());
        return response(repository.save(view));
    }

    public void delete(Long id, Long owner) {
        repository.delete(require(id, owner));
    }
}
