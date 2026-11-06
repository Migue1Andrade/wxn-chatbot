import angular from 'angular';
import template from './department-directory.html?raw';
angular.module('wxn.members').directive('departmentDirectory', function () {
  return {
    restrict: 'E',
    scope: {},
    bindToController: { departments: '<', loading: '<', onEdit: '&', onRemove: '&', onMembers: '&' },
    controllerAs: '$ctrl',
    template,
    controller: function () {
      const vm = this;
      vm.query = '';
      vm.status = '';
      vm.sort = 'name';
      vm.page = 0;
      vm.pageSize = 12;
      vm.filter = () => {
        const query = vm.query.trim().toLocaleLowerCase('pt-BR');
        vm.filtered = (vm.departments || []).filter(
          (d) =>
            (!vm.status || d.status === vm.status) &&
            [d.name, d.description].some((value) => (value || '').toLocaleLowerCase('pt-BR').includes(query)),
        );
        vm.filtered.sort((a, b) =>
          vm.sort === 'members'
            ? b.memberCount - a.memberCount || a.name.localeCompare(b.name, 'pt-BR')
            : a.name.localeCompare(b.name, 'pt-BR'),
        );
        vm.page = 0;
        vm.paginate();
      };
      vm.paginate = (page = vm.page) => {
        vm.page = page;
        vm.visible = vm.filtered.slice(vm.page * vm.pageSize, (vm.page + 1) * vm.pageSize);
      };
      vm.$onChanges = () => {
        vm.activeCount = (vm.departments || []).filter((d) => d.status === 'ACTIVE').length;
        vm.filter();
      };
      vm.clear = () => {
        vm.query = '';
        vm.status = '';
        vm.filter();
      };
    },
  };
});
