import angular from 'angular';
import template from '../templates/user-directory.html?raw';
import { cpfDigits } from '../../../shared/cpf.js';

angular.module('wxn.users').directive('userDirectory', function () {
  return {
    restrict: 'E',
    scope: {},
    bindToController: { users: '<', loading: '<', onEdit: '&' },
    controllerAs: '$ctrl',
    template,
    controller: function () {
      this.page = 0;
      this.pageSize = 12;
      this.sort = 'name';
      this.query = '';
      this.role = '';
      this.status = '';
      this.filteredUsers = [];
      this.$onChanges = () => {
        this.activeCount = (this.users || []).filter((user) => user.accountStatus === 'ACTIVE').length;
        this.filter();
      };
      this.initials = (name) => {
        const parts = (name || '').trim().split(/\s+/);
        return (parts[0].charAt(0) + (parts.length > 1 ? parts.at(-1).charAt(0) : '')).toUpperCase();
      };
      this.paginate = (page = this.page) => {
        this.page = page;
        this.visibleUsers = this.filteredUsers.slice(
          this.page * this.pageSize,
          (this.page + 1) * this.pageSize,
        );
      };
      this.filter = () => {
        const query = this.query.trim().toLocaleLowerCase('pt-BR');
        const digits = cpfDigits(query);
        this.filteredUsers = (this.users || []).filter((user) => {
          if (this.role && user.role !== this.role) {
            return false;
          }
          if (this.status && user.accountStatus !== this.status) {
            return false;
          }
          const values = [
            user.name,
            user.username,
            user.email,
            ...(user.departments || []).map((d) => d.name),
          ];
          return (
            !query ||
            values.some((value) => (value || '').toLocaleLowerCase('pt-BR').includes(query)) ||
            (digits.length >= 3 && [user.cpf, user.phone].some((value) => cpfDigits(value).includes(digits)))
          );
        });
        this.filteredUsers.sort((a, b) =>
          this.sort === 'recent'
            ? (Date.parse(b.createdAt) || 0) - (Date.parse(a.createdAt) || 0)
            : a.name.localeCompare(b.name, 'pt-BR'),
        );
        this.page = 0;
        this.paginate();
      };
      this.clear = () => {
        this.query = '';
        this.role = '';
        this.status = '';
        this.filter();
      };
    },
  };
});
