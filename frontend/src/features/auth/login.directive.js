import angular from 'angular';
import template from './login.html?raw';
angular.module('wxn.core').directive('accountLogin', function () {
  return {
    restrict: 'E',
    scope: {},
    controllerAs: 'vm',
    template,
    controller: [
      'AccountSessionService',
      '$location',
      function (session, $location) {
        this.form = {};
        this.login = () => {
          if (this.busy) {
            return;
          }
          this.busy = true;
          this.error = '';
          session
            .login(this.form.username, this.form.password)
            .then(() => {
              this.form.password = '';
              $location.path('/');
            })
            .catch((error) => {
              this.error = error.userMessage || 'Não foi possível entrar.';
              this.form.password = '';
            })
            .finally(() => {
              this.busy = false;
            });
        };
      },
    ],
  };
});
