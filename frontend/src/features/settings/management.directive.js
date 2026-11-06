import angular from 'angular';
import template from './management.html?raw';
import tagTemplate from './department-tag.html?raw';
import selectorTemplate from './department-select.html?raw';
angular
  .module('wxn.members')
  .directive('departmentTag', function () {
    return {
      restrict: 'E',
      scope: {},
      bindToController: { department: '<' },
      controllerAs: '$ctrl',
      controller: function () {
        this.$onChanges = () => {
          const color = /^#[0-9a-f]{6}$/i.test(this.department?.color || '')
            ? this.department.color
            : '#187566';
          let rgb = [1, 3, 5].map((index) => parseInt(color.slice(index, index + 2), 16));
          const background = rgb.map((value) => Math.round(255 * 0.92 + value * 0.08));
          const luminance = (values) =>
            values
              .map((value) => {
                const n = value / 255;
                return n <= 0.04045 ? n / 12.92 : ((n + 0.055) / 1.055) ** 2.4;
              })
              .reduce((sum, value, index) => sum + value * [0.2126, 0.7152, 0.0722][index], 0);
          for (let i = 0; i < 10 && (luminance(background) + 0.05) / (luminance(rgb) + 0.05) < 4.5; i++) {
            rgb = rgb.map((value) => Math.floor(value * 0.82));
          }
          this.colors = {
            color: 'rgb(' + rgb.join(',') + ')',
            'background-color': 'rgb(' + background.join(',') + ')',
            'border-color': color + '55',
          };
        };
      },
      template: tagTemplate,
    };
  })
  .directive('departmentSelect', function () {
    return {
      restrict: 'E',
      scope: {},
      bindToController: { departments: '<', selected: '=' },
      controllerAs: '$ctrl',
      template: selectorTemplate,
      controller: function () {
        this.toggle = (id) => {
          const index = this.selected.indexOf(id);
          if (index >= 0) {
            this.selected.splice(index, 1);
          } else {
            this.selected.push(id);
          }
        };
      },
    };
  })
  .directive('managementPage', function () {
    return {
      restrict: 'E',
      scope: {},
      controllerAs: 'vm',
      template,
      controller: [
        '$location',
        'ManagementService',
        'AccountSessionService',
        function ($location, service, session) {
          const vm = this;
          vm.path = $location.path();
          vm.isSettings = vm.path.startsWith('/settings');
          vm.isDepartments = vm.path === '/settings/departments';
          vm.isMembers = vm.path === '/members';
          vm.departments = [];
          vm.members = [];
          vm.error = '';
          vm.loading = false;
          const error = (e) => {
            vm.error = e.userMessage || 'Não foi possível concluir a operação.';
          };
          vm.load = () => {
            vm.loading = true;
            vm.error = '';
            return service
              .departments()
              .then((rows) => {
                vm.departments = rows;
                return vm.isMembers ? service.users() : [];
              })
              .then((rows) => {
                vm.members = rows;
              })
              .catch(error)
              .finally(() => {
                vm.loading = false;
              });
          };
          vm.edit = (item) => {
            vm.notice = '';
            vm.error = '';
            vm.form = vm.isDepartments
              ? {
                  expectedVersion: item?.version,
                  ...(item || { name: '', color: '#2563EB', description: '', status: 'ACTIVE' }),
                }
              : {
                  id: item?.id,
                  name: item?.name || '',
                  phone: item?.phone || '',
                  username: item?.username || '',
                  email: item?.email || '',
                  cpf: item?.cpf || '',
                  role: item?.role || 'ATTENDANT',
                  accountStatus: item?.accountStatus || 'ACTIVE',
                  registrationIncomplete: item?.registrationIncomplete || false,
                  password: '',
                  passwordConfirmation: '',
                  departmentIds: (item?.departments || []).map((d) => d.id),
                };
          };
          vm.save = () => {
            if (vm.busy) {
              return;
            }
            if (vm.isMembers && vm.needsPassword() && vm.form.password !== vm.form.passwordConfirmation) {
              vm.error = 'A confirmação de senha não coincide.';
              return;
            }
            vm.busy = true;
            vm.error = '';
            (vm.isDepartments ? service.saveDepartment(vm.form) : service.saveUser(vm.form))
              .then(() => {
                vm.form = null;
                vm.notice = vm.isDepartments ? 'Setor salvo com sucesso.' : 'Usuário salvo com sucesso.';
                return session.load().then((user) => {
                  if (user.role !== 'ADMIN') {
                    $location.path('/');
                    return;
                  }
                  return vm.load();
                });
              })
              .catch(error)
              .finally(() => {
                vm.busy = false;
              });
          };
          vm.showMembers = (department) => {
            vm.membersPanel = department;
            vm.departmentMembers = [];
            vm.membersLoading = true;
            vm.membersError = '';
            service
              .departmentMembers(department.id)
              .then((rows) => {
                if (vm.membersPanel?.id === department.id) {
                  vm.departmentMembers = rows;
                }
              })
              .catch((e) => {
                if (vm.membersPanel?.id === department.id) {
                  vm.membersError = e.userMessage;
                }
              })
              .finally(() => {
                if (vm.membersPanel?.id === department.id) {
                  vm.membersLoading = false;
                }
              });
          };
          vm.remove = (item) => {
            vm.deleting = item;
          };
          vm.confirmDelete = () => {
            vm.busy = true;
            vm.error = '';
            service
              .deleteDepartment(vm.deleting.id)
              .then(() => {
                vm.deleting = null;
                vm.notice = 'Setor excluído com sucesso.';
                return vm.load();
              })
              .catch(error)
              .finally(() => {
                vm.busy = false;
              });
          };
          vm.needsPassword = () => Boolean(vm.form && (!vm.form.id || vm.form.registrationIncomplete));
          if (vm.isDepartments || vm.isMembers) {
            vm.load();
          }
        },
      ],
    };
  });
