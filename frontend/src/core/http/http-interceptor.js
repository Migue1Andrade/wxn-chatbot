import angular from 'angular';

angular
  .module('wxn.core')
  .factory('ApiHttpInterceptor', [
    '$q',
    'AccountSessionState',
    '$location',
    '$injector',
    'environment',
    'EventBusService',
    'AppEvents',
    function ($q, session, $location, $injector, environment, bus, events) {
      return {
        request(config) {
          if (config.url.startsWith(environment.apiBaseUrl + '/api/')) {
            config.headers['X-Dev-Api-Key'] = environment.devApiKey;
            config.withCredentials = true;
            if (session.csrfToken) {
              config.headers['X-CSRF-Token'] = session.csrfToken;
            }
          }
          return config;
        },
        responseError(response) {
          if (!response.config.url.startsWith(environment.apiBaseUrl + '/api/')) {
            return $q.reject(response);
          }
          if (response.status === 401 && !response.config.url.includes('/api/auth/')) {
            session.user = null;
            session.ready = false;
            session.csrfToken = '';
            $injector.get('SocketService').disconnect();
            $location.path('/login');
          }
          const messages = {
            0: 'Não foi possível conectar. Verifique sua conexão e tente novamente.',
            400: 'Verifique os campos e formatos informados.',
            401: 'Sua sessão expirou. Entre novamente.',
            403: 'Você não tem permissão para acessar este recurso.',
            404: 'O recurso solicitado não foi encontrado.',
            409: 'Os dados foram alterados ou há um conflito. Atualize e tente novamente.',
            422: 'Não foi possível validar os dados informados.',
            429: 'Muitas solicitações. Aguarde um momento e tente novamente.',
            500: 'Não foi possível concluir a operação. Tente novamente.',
          };
          response.userMessage = response.data?.detail || messages[response.status] || messages[500];
          response.fieldErrors = response.data?.errors || {};
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
