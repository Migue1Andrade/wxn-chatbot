import angular from 'angular';
angular.module('wxn.chats').factory('QuickRepliesService', [
  'ApiClientService',
  function (api) {
    return {
      list: (page, scope) => api.get('/quick-replies', { page, scope }),
      save: (form) =>
        form.id ? api.patch('/quick-replies/' + form.id, form) : api.post('/quick-replies', form),
      remove: (id) => api.delete('/quick-replies/' + id),
    };
  },
]);
