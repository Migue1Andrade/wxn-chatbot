import angular from 'angular';
import template from './whatsapp-setup.html?raw';

angular.module('wxn.core').directive('whatsappSetup', [
  '$interval',
  '$location',
  'WhatsAppBootstrapService',
  function ($interval, $location, setup) {
    return {
      restrict: 'E',
      scope: {},
      controllerAs: 'vm',
      template,
      controller: function () {
        const vm = this;
        let poll;
        vm.form = {};
        vm.status = 'INITIALIZING';
        vm.load = () => {
          vm.loading = true;
          vm.error = '';
          setup
            .status()
            .then((result) => {
              vm.result = result;
              vm.status = result.state;
              if (result.completed) {
                vm.status = 'CONNECTED';
                vm.redirecting = true;
                window.setTimeout(() => $location.path('/login'), 1200);
              }
            })
            .catch((error) => {
              vm.error = error.userMessage || 'Não foi possível consultar a Evolution API.';
              vm.status = 'ERROR';
            })
            .finally(() => {
              vm.loading = false;
            });
        };
        vm.initialize = () => {
          if (!vm.form.token || vm.busy) {
            return;
          }
          vm.busy = true;
          vm.error = '';
          setup
            .initialize(vm.form.token)
            .then((result) => {
              vm.result = result;
              vm.status = result.state;
              vm.startPolling();
            })
            .catch((error) => {
              vm.error = error.userMessage || 'Não foi possível inicializar a conexão.';
              vm.status = 'ERROR';
            })
            .finally(() => {
              vm.busy = false;
            });
        };
        vm.refresh = () => {
          if (!vm.form.token || vm.busy) {
            return;
          }
          vm.busy = true;
          setup
            .refresh(vm.form.token)
            .then((result) => {
              vm.result = result;
              vm.status = result.state;
              if (result.completed || result.state === 'CONNECTED') {
                vm.stopPolling();
                vm.redirecting = true;
                window.setTimeout(() => $location.path('/login'), 1200);
              }
            })
            .catch((error) => {
              vm.error = error.userMessage || 'Não foi possível consultar a conexão.';
              vm.status = 'ERROR';
            })
            .finally(() => {
              vm.busy = false;
            });
        };
        vm.startPolling = () => {
          vm.stopPolling();
          poll = $interval(vm.refresh, 3000);
        };
        vm.stopPolling = () => {
          if (poll) {
            $interval.cancel(poll);
            poll = null;
          }
        };
        vm.$onDestroy = vm.stopPolling;
        vm.load();
      },
    };
  },
]);
