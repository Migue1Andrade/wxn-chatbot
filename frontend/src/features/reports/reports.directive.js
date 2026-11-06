import angular from 'angular';
import template from './reports.html?raw';
angular.module('wxn.chats').directive('reportsPage', function () {
  return {
    restrict: 'E',
    scope: {},
    controllerAs: 'vm',
    template,
    controller: [
      'ReportsService',
      function (reports) {
        const vm = this;
        vm.export = () => {
          if (vm.busy) {
            return;
          }
          vm.busy = true;
          vm.error = '';
          reports
            .exportConversations()
            .then((blob) => {
              const url = URL.createObjectURL(blob);
              const anchor = document.createElement('a');
              anchor.href = url;
              anchor.download = 'atendimentos.csv';
              anchor.click();
              URL.revokeObjectURL(url);
            })
            .catch(() => {
              vm.error = 'Não foi possível exportar os atendimentos.';
            })
            .finally(() => {
              vm.busy = false;
            });
        };
      },
    ],
  };
});
