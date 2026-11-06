import angular from 'angular';
angular.module('wxn.members').factory('ManagementService', [
  'ApiClientService',
  'UsersService',
  function (api, users) {
    return {
      departments: () => api.get('/departments'),
      users: () => users.listUsers(),
      saveDepartment: (form) =>
        form.id
          ? api.patch('/departments/' + form.id, {
              name: form.name,
              color: form.color,
              description: form.description,
              status: form.status,
              expectedVersion: form.expectedVersion,
            })
          : api.post('/departments', form),
      departmentMembers: (id) => api.get('/departments/' + id + '/members'),
      deleteDepartment: (id) => api.delete('/departments/' + id),
      saveUser: (form) => users.saveUser(form),
    };
  },
]);
