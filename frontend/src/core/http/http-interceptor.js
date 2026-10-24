import angular from 'angular';

angular
  .module('wxn.core')
  .factory('ApiHttpInterceptor', [
    '$q',
    'environment',
    'EventBusService',
    'AppEvents',
    function ($q, environment, bus, events) {
      return {
        request(config) {
          if (config.url.startsWith(environment.apiBaseUrl + '/api/')) {
            config.headers['X-Dev-Api-Key'] = environment.devApiKey;
          }
          return config;
        },
        responseError(response) {
          if (!response.config.url.startsWith(environment.apiBaseUrl + '/api/')) return $q.reject(response);
          response.userMessage =
            response.data?.detail ||
            (response.status === 401 || response.status === 403
              ? 'Acesso ao ambiente de desenvolvimento indisponível.'
              : 'Não foi possível conectar. Tente novamente.');
          bus.emit(events.HTTP_ERROR, response.userMessage);
          return $q.reject(response);
        },
      };
    },
  ])
  .config([
    '$httpProvider',
    function ($httpProvider) {
      $httpProvider.interceptors.push('ApiHttpInterceptor');
    },
  ]);
