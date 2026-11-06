import angular from 'angular';
export const ConversationActions = Object.freeze({
  ASSUME: 'assume',
  ASSIGN: 'assign',
  TRANSFER: 'transfer',
  CLOSE: 'close',
  REOPEN: 'reopen',
});
angular.module('wxn.chats').factory('ConversationActionsService', [
  'ApiClientService',
  function (api) {
    return {
      execute(id, action, payload) {
        if (!Object.values(ConversationActions).includes(action)) {
          throw new Error('Ação de atendimento inválida.');
        }
        return api.patch('/conversations/' + encodeURIComponent(id) + '/' + action, payload);
      },
    };
  },
]);
