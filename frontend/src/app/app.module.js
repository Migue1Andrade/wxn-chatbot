import angular from 'angular';
import 'angular-route';
import { environment } from '../core/config/environment.js';
angular
  .module('wxnDashboard', [
    'ngRoute',
    'wxn.core',
    'wxn.chats',
    'wxn.messages',
    'wxn.users',
    'wxn.members',
    'wxn.channels',
  ])
  .constant('environment', environment)
  .constant('appConfig', environment);
