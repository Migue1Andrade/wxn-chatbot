import angular from 'angular';

angular.module('wxn.core').factory('EventBusService', [
  '$rootScope',
  function ($rootScope) {
    return {
      emit(name, payload) {
        $rootScope.$evalAsync(() => $rootScope.$emit(name, payload));
      },
      on(name, listener) {
        const unsubscribe = $rootScope.$on(name, (event, payload) => listener(payload));
        return unsubscribe;
      },
    };
  },
]);
