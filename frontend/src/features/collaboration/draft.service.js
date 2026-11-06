import angular from 'angular';
angular.module('wxn.core').factory('ConversationDraftService', [
  'ApiClientService',
  '$q',
  function (api, $q) {
    const pending = new Map();
    function key(id, mode) {
      return id + ':' + mode;
    }
    return {
      save(id, mode, content) {
        const name = key(id, mode);
        const operation = (pending.get(name) || $q.resolve())
          .catch(() => {})
          .then(() => api.put('/conversations/' + id + '/draft', { mode, content }));
        pending.set(name, operation);
        operation
          .finally(() => {
            if (pending.get(name) === operation) {
              pending.delete(name);
            }
          })
          .catch(() => {});
        return operation;
      },
      load(id, mode) {
        return (pending.get(key(id, mode)) || $q.resolve())
          .catch(() => {})
          .then(() => api.get('/conversations/' + id + '/draft', { mode }));
      },
    };
  },
]);
