import angular from 'angular';
angular.module('wxn.chats').factory('TasksService', [
  'ApiClientService',
  function (api) {
    return {
      get: (id) => api.get('/tasks/' + id),
      list: (params) => api.get('/tasks', params),
      users: () => api.get('/users'),
      save: (form) => (form.id ? api.patch('/tasks/' + form.id, form) : api.post('/tasks', form)),
      transition: (task, status) =>
        api.patch('/tasks/' + task.id, { ...task, expectedVersion: task.version, status }),
    };
  },
]);
