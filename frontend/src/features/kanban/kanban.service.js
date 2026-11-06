import angular from 'angular';
angular.module('wxn.chats').factory('KanbanService', [
  'ApiClientService',
  function (api) {
    const root = '/admin/kanban';
    return {
      page: (filters) => api.get(root + '/conversations', filters),
      metrics: () => api.get(root + '/metrics'),
      details: (id) => api.get(root + '/conversations/' + id),
      views: () => api.get(root + '/views'),
      saveView: (name, configuration) =>
        api.post(root + '/views', { name, configuration: JSON.stringify(configuration) }),
      deleteView: (id) => api.delete(root + '/views/' + id),
      priority: (card, priority) =>
        api.patch(root + '/conversations/' + card.conversation.id + '/priority', {
          priority,
          expectedVersion: card.conversation.version,
        }),
      status: (card, status, actorMemberId) =>
        api.patch('/conversations/' + card.conversation.id + '/status?status=' + status, {
          actorMemberId,
          expectedVersion: card.conversation.version,
        }),
      assign: (card, memberId, actorMemberId) =>
        api.patch(
          '/conversations/' +
            card.conversation.id +
            (card.conversation.assignedMemberId ? '/transfer' : '/assign'),
          { memberId, actorMemberId, expectedVersion: card.conversation.version },
        ),
      history: (id) => api.get('/conversations/' + id + '/events', { size: 20, order: 'DESC' }),
    };
  },
]);
