import angular from 'angular';
import template from './tasks.html?raw';
angular.module('wxn.chats').directive('tasksPage', function () {
  return {
    restrict: 'E',
    scope: {},
    controllerAs: 'vm',
    template,
    controller: [
      'TasksService',
      'AccountSessionState',
      '$location',
      function (tasks, account, $location) {
        const vm = this;
        vm.users = [account.user];
        vm.status = 'OPEN';
        vm.rows = [];
        vm.types = { TASK: 'Tarefa', FOLLOW_UP: 'Acompanhamento', REMINDER: 'Lembrete' };
        vm.priorities = { LOW: 'Baixa', NORMAL: 'Normal', HIGH: 'Alta', URGENT: 'Urgente' };
        const error = (e) => {
          vm.error = e.userMessage || 'Não foi possível concluir a operação.';
        };
        if (account.user.role === 'ADMIN') {
          tasks
            .users()
            .then((rows) => {
              vm.users = rows;
            })
            .catch(error);
        }
        vm.userName = (id) => vm.users.find((user) => user.id === id)?.name || 'Usuário #' + id;
        vm.load = (more = false) => {
          vm.loading = true;
          vm.page = more ? vm.page + 1 : 0;
          tasks
            .list({ page: vm.page, status: vm.status || undefined })
            .then((rows) => {
              vm.rows = more ? vm.rows.concat(rows) : rows;
              vm.more = rows.length === 30;
            })
            .catch(error)
            .finally(() => {
              vm.loading = false;
            });
        };
        vm.edit = (task) => {
          vm.error = '';
          vm.form = task
            ? { ...task, dueAt: new Date(task.dueAt), expectedVersion: task.version }
            : {
                type: 'TASK',
                title: '',
                description: '',
                assignedUserId: account.user.id,
                conversationId: $location.search().conversation || null,
                dueAt: new Date(Date.now() + 3600000),
                status: 'OPEN',
                priority: 'NORMAL',
              };
        };
        vm.save = () => {
          if (vm.busy) {
            return;
          }
          vm.busy = true;
          tasks
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
        vm.finish = (task, status) => {
          vm.busy = true;
          tasks
            .transition(task, status)
            .then(() => vm.load())
            .catch(error)
            .finally(() => {
              vm.busy = false;
            });
        };
        vm.overdue = (task) => task.status === 'OPEN' && Date.parse(task.dueAt) < Date.now();
        vm.load();
        if ($location.search().conversation) {
          vm.edit();
        }
      },
    ],
  };
});
