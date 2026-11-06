import angular from 'angular';
angular.module('wxn.users').factory('UsersService', [
  'ApiClientService',
  function (api) {
    return {
      saveUser(form) {
        const body = {
          name: form.name,
          username: form.username,
          email: form.email,
          cpf: form.cpf,
          phone: form.phone,
          role: form.role,
          accountStatus: form.accountStatus,
          departmentIds: form.departmentIds,
        };
        if (!form.id) {
          body.password = form.password;
          body.passwordConfirmation = form.passwordConfirmation;
        } else if (form.registrationIncomplete) {
          body.initialPassword = form.password;
          body.passwordConfirmation = form.passwordConfirmation;
        }
        return form.id ? api.patch('/users/' + form.id, body) : api.post('/users', body);
      },
      listUsers() {
        return api.get('/users');
      },
    };
  },
]);
