import angular from 'angular';
import template from './password-reset.html?raw';

angular.module('wxn.core').directive('passwordReset', function () {
  return {
    restrict: 'E',
    scope: {},
    controllerAs: 'vm',
    template,
    controller: [
      'ApiClientService',
      '$location',
      function (api, $location) {
        this.token = $location.search().token || '';
        this.form = {};
        this.submit = () => {
          if (this.busy || !this.token || this.form.password !== this.form.confirmPassword) {
            return;
          }
          this.busy = true;
          this.error = '';
          api
            .post('/auth/password-reset/confirm', { token: this.token, password: this.form.password })
            .then(() => {
              this.success = true;
              this.token = '';
              this.form = {};
            })
            .catch(() => {
              this.error = 'Este link é inválido, expirou ou já foi utilizado. Solicite outro link.';
            })
            .finally(() => {
              this.busy = false;
            });
        };
      },
    ],
  };
});
