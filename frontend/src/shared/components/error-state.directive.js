import angular from 'angular';
import template from './error-state.html?raw';
angular.module('wxn.core').directive('errorState', function () {
  return {
    restrict: 'E',
    scope: {},
    bindToController: { message: '@', onRetry: '&?' },
    controllerAs: '$ctrl',
    controller: function () {},
    template,
  };
});
