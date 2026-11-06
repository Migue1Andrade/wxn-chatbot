import angular from 'angular';
import { AsyncState } from '../../core/state/async-state.js';

angular.module('wxn.messages').factory('MessageSendingService', [
  'MessagesService',
  'EventBusService',
  'MessageEvents',
  function (messages, bus, events) {
    return {
      create() {
        const operations = new Map();
        function operation(id) {
          if (!operations.has(id)) {
            operations.set(id, new AsyncState());
          }
          return operations.get(id);
        }
        function send(conversation, member, content, reply, operationId) {
          const state = operation(conversation.id);
          if (state.loading) {
            return;
          }
          if (!member || conversation.status === 'CLOSED' || !conversation.channelId) {
            state.fail(
              new Error(
                !member
                  ? 'Selecione um atendente ativo.'
                  : 'Reabra o atendimento e confirme o canal antes de enviar.',
              ),
            );
            return;
          }
          const text = content?.trim();
          if (!text || text.length > 1600) {
            state.fail(new Error('Informe uma mensagem de até 1600 caracteres.'));
            return;
          }
          state.begin();
          return messages
            .sendMessage(conversation.id, {
              content: text,
              operationId,
              replyToMessageId: reply?.id,
            })
            .then((message) => {
              const failed = ['FAILED', 'UNKNOWN'].includes(message.status);
              bus.emit(failed ? events.STATUS_UPDATED : events.SENT, {
                conversationId: conversation.id,
                payload: message,
              });
              if (failed) {
                state.fail(
                  new Error(
                    message.status === 'UNKNOWN' || message.errorCode === 'TWILIO_TRANSPORT_UNKNOWN'
                      ? 'Resultado remoto não confirmado. Consulte o histórico antes de repetir.'
                      : 'A tentativa foi registrada, mas o provedor não enviou a mensagem.',
                  ),
                );
              } else {
                state.succeed();
              }
              return message;
            })
            .catch((error) => {
              state.fail(error);
            });
        }
        return { operation, send };
      },
    };
  },
]);
