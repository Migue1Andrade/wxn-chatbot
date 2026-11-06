import angular from 'angular';
import template from './settings-hub.html?raw';
import breadcrumbTemplate from './settings-breadcrumb.html?raw';
import { settingsCatalog } from './settings-catalog.js';
angular
  .module('wxn.members')
  .directive('settingsHub', function () {
    return {
      restrict: 'E',
      scope: {},
      controllerAs: 'settings',
      template,
      controller: [
        'AccountSessionState',
        function (account) {
          this.query = '';
          this.filter = () => {
            const term = this.query.trim().toLocaleLowerCase('pt-BR');
            const available = settingsCatalog.filter(
              (item) =>
                (!item.admin || account.user?.role === 'ADMIN') &&
                [item.title, item.description, item.category]
                  .join(' ')
                  .toLocaleLowerCase('pt-BR')
                  .includes(term),
            );
            this.groups = [...new Set(available.map((item) => item.category))].map((name) => ({
              name,
              items: available.filter((item) => item.category === name),
            }));
            this.total = available.length;
          };
          this.filter();
        },
      ],
    };
  })
  .directive('settingsBreadcrumb', function () {
    return {
      restrict: 'E',
      scope: {},
      controllerAs: 'crumb',
      template: breadcrumbTemplate,
      controller: [
        '$location',
        function ($location) {
          const entry = settingsCatalog.find((item) => item.path === $location.path());
          this.category = entry?.category || 'Operação';
          this.label = entry?.title || 'Configurações';
        },
      ],
    };
  });
