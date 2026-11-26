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
        vm.hasConversationContext = Boolean($location.search().conversation);
        vm.users = [account.user];
        vm.status = 'OPEN';
        vm.type = 'FOLLOW_UP';
        vm.period = 'OVERDUE';
        vm.rows = [];
        vm.types = { TASK: 'Tarefa', FOLLOW_UP: 'Retorno', REMINDER: 'Lembrete' };
        vm.formTypes = () => {
          if (vm.form?.conversationId) {
            return vm.types;
          }
          return { TASK: vm.types.TASK, REMINDER: vm.types.REMINDER };
        };
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
            .list({
              page: vm.page,
              status: vm.status || undefined,
              type: vm.type || undefined,
              period: vm.status === 'OPEN' ? vm.period || undefined : undefined,
            })
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
                type: $location.search().conversation ? 'FOLLOW_UP' : 'TASK',
                title: '',
                description: '',
                assignedUserId: account.user.id,
                conversationId: $location.search().conversation || null,
                dueAt: new Date(Date.now() + 3600000),
                status: 'OPEN',
                priority: 'NORMAL',
              };
        };
        vm.selectPeriod = (period) => {
          vm.status = 'OPEN';
          vm.period = period;
          vm.load();
        };
        vm.selectStatus = (status) => {
          vm.status = status;
          if (status !== 'OPEN') {
            vm.period = '';
          } else if (vm.type === 'FOLLOW_UP' && !vm.period) {
            vm.period = 'OVERDUE';
          }
          vm.load();
        };
        vm.selectType = (type) => {
          vm.type = type;
          if (type === 'FOLLOW_UP') {
            vm.status = 'OPEN';
          }
          if (type !== 'FOLLOW_UP') {
            vm.period = '';
          } else if (!vm.period) {
            vm.period = 'OVERDUE';
          }
          vm.load();
        };
        vm.reschedule = (task) => vm.edit(task);
        vm.save = () => {
          if (vm.busy) {
            return;
          }
          vm.busy = true;
          const savedType = vm.form.type;
          tasks
            .save(vm.form)
            .then(() => {
              vm.form = null;
              vm.notice = 'Alterações salvas com sucesso.';
              if (savedType !== vm.type) {
                vm.type = savedType;
                vm.period = '';
              }
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
        vm.overdue = (task) =>
          task.type === 'FOLLOW_UP' && task.status === 'OPEN' && Date.parse(task.dueAt) < Date.now();
        vm.load();
        if ($location.search().conversation) {
          vm.edit();
        }
      },
    ],
  };
});
