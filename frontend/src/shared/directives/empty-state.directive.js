import angular from 'angular';
import template from '../templates/empty-state.html?raw';
angular.module('wxn.core').directive('emptyState', function () {
  return {
    restrict: 'E',
    scope: {},
    bindToController: { title: '@', description: '@' },
    controllerAs: '$ctrl',
    controller: function () {},
    template,
  };
});
