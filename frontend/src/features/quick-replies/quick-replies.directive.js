import angular from 'angular';
import template from './quick-replies.html?raw';
angular.module('wxn.chats').directive('quickRepliesPage', function () {
  return {
    restrict: 'E',
    scope: {},
    controllerAs: 'vm',
    template,
    controller: [
      '$location',
      'QuickRepliesService',
      'AccountSessionState',
      function ($location, replies, account) {
        const vm = this;
        vm.global = $location.path().includes('global-');
        vm.account = account;
        vm.rows = [];
        vm.page = 0;
        const error = (e) => {
          vm.error = e.userMessage || 'Não foi possível concluir a operação.';
        };
        vm.load = (more = false) => {
          vm.loading = true;
          vm.page = more ? vm.page + 1 : 0;
          replies
            .list(vm.page, vm.global ? 'GLOBAL' : 'PERSONAL')
            .then((rows) => {
              vm.more = rows.length === 100;
              vm.rows = more ? vm.rows.concat(rows) : rows;
            })
            .catch(error)
            .finally(() => {
              vm.loading = false;
            });
        };
        vm.edit = (row) => {
          vm.error = '';
          vm.form = row
            ? { ...row, expectedVersion: row.version }
            : {
                name: '',
                shortcut: '',
                description: '',
                content: '',
                scope: vm.global ? 'GLOBAL' : 'PERSONAL',
                active: true,
              };
        };
        vm.duplicate = (row) => {
          vm.edit(row);
          delete vm.form.id;
          vm.form.name += ' (cópia)';
          vm.form.shortcut += '_copia';
          vm.form.scope = vm.global ? 'GLOBAL' : 'PERSONAL';
        };
        vm.save = () => {
          if (vm.busy) {
            return;
          }
          vm.busy = true;
          replies
            .save(vm.form)
            .then(() => {
              vm.form = null;
              vm.notice = 'Alterações salvas com sucesso.';
              vm.load();
            })
            .catch(error)
            .finally(() => {
              vm.busy = false;
            });
        };
        vm.remove = (row) => {
          vm.deleting = row;
        };
        vm.confirmDelete = () => {
          vm.busy = true;
          replies
            .remove(vm.deleting.id)
            .then(() => {
              vm.deleting = null;
              vm.load();
            })
            .catch(error)
            .finally(() => {
              vm.busy = false;
            });
        };
        vm.load();
      },
    ],
  };
});
