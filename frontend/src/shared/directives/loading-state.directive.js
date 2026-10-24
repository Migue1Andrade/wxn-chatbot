import angular from 'angular';
import template from '../templates/loading-state.html?raw';
angular.module('wxn.core').directive('loadingState', function () {
  return {
    restrict: 'E',
    scope: {},
    bindToController: { label: '@' },
    controllerAs: '$ctrl',
    controller: function () {},
    template,
  };
});
