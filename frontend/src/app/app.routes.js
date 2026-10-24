import angular from 'angular';
angular.module('wxnDashboard').config([
  '$routeProvider',
  function ($routeProvider) {
    $routeProvider.when('/', { template: '<chat-layout></chat-layout>' }).otherwise({ redirectTo: '/' });
  },
]);
