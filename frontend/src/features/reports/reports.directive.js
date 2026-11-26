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
        vm.filters = {};
        const localDate = (value) => {
          if (!value) {
            return '';
          }
          const date = value instanceof Date ? value : new Date(value);
          if (Number.isNaN(date.getTime())) {
            return '';
          }
          const year = date.getFullYear();
          const month = String(date.getMonth() + 1).padStart(2, '0');
          const day = String(date.getDate()).padStart(2, '0');
          return `${year}-${month}-${day}`;
        };
        vm.export = () => {
          if (vm.busy) {
            return;
          }
          vm.busy = true;
          vm.error = '';
          vm.success = '';
          const filters = {};
          filters.from = localDate(vm.filters.from);
          filters.to = localDate(vm.filters.to);
          if (filters.from && filters.to && filters.to < filters.from) {
            vm.error = 'A data final deve ser igual ou posterior à inicial.';
            vm.busy = false;
            return;
          }
          if (vm.filters.status) {
            const allowedStatuses = [
              'PENDING',
              'IN_PROGRESS',
              'WAITING_CUSTOMER',
              'RESOLVED',
              'CLOSED',
              'CANCELLED',
            ];
            if (!allowedStatuses.includes(vm.filters.status)) {
              vm.error = 'Selecione um status válido.';
              vm.busy = false;
              return;
            }
            filters.status = vm.filters.status;
          }
          reports
            .exportConversations(filters)
            .then((blob) => {
              const url = URL.createObjectURL(blob);
              const anchor = document.createElement('a');
              anchor.href = url;
              anchor.download = 'atendimentos.csv';
              anchor.click();
              vm.success = 'Arquivo CSV preparado com os filtros selecionados.';
              window.setTimeout(() => URL.revokeObjectURL(url), 1000);
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
