import angular from 'angular';
import template from './sla.html?raw';
angular.module('wxn.chats').directive('slaSettings', function () {
  return {
    restrict: 'E',
    scope: {},
    controllerAs: 'vm',
    template,
    controller: [
      'SlaPolicyService',
      function (service) {
        const vm = this;
        const error = (e) => {
          vm.error = e.userMessage || 'Não foi possível salvar a política.';
        };
        vm.load = () => {
          vm.loading = true;
          vm.error = '';
          service
            .load()
            .then((policy) => {
              vm.policy = policy;
            })
            .catch(error)
            .finally(() => {
              vm.loading = false;
            });
        };
        vm.load();
        vm.save = () => {
          vm.busy = true;
          vm.error = '';
          vm.saved = false;
          service
            .save(vm.policy)
            .then((policy) => {
              vm.policy = policy;
              vm.saved = true;
            })
            .catch(error)
            .finally(() => {
              vm.busy = false;
            });
        };
      },
    ],
  };
});
