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
      'ApiClientService',
      '$location',
      function (session, api, $location) {
        this.form = {};
        this.requestReset = () => {
          if (this.resetBusy || !this.resetEmail) {
            return;
          }
          this.resetBusy = true;
          this.resetError = '';
          api
            .post('/auth/password-reset/request', { email: this.resetEmail })
            .then(() => {
              this.resetSent = true;
            })
            .catch(() => {
              this.resetError = 'Não foi possível enviar a solicitação. Tente novamente.';
            })
            .finally(() => {
              this.resetBusy = false;
            });
        };
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
