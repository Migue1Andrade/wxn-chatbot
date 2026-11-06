import angular from 'angular';

angular.module('wxn.messages').factory('MessageReadContextService', [
  'ConversationReadService',
  'DevMemberContextService',
  'EventBusService',
  'ChatEvents',
  function (reads, members, bus, events) {
    return {
      create() {
        let conversationId = null;
        let visibleId = null;
        let marked = null;
        let disposed = false;
        const pending = new Set();
        const state = { error: null };
        function mark() {
          const member = members.state.member;
          if (!member || !visibleId || document.visibilityState !== 'visible' || disposed) {
            return;
          }
          const id = conversationId;
          const anchor = visibleId;
          const key = member.id + ':' + id + ':' + anchor;
          if (key === marked || pending.has(key)) {
            return;
          }
          pending.add(key);
          state.error = null;
          return reads
            .markRead(id, { memberId: member.id, throughMessageId: anchor })
            .then((payload) => {
              if (
                !disposed &&
                id === conversationId &&
                members.state.member?.id === member.id &&
                visibleId === anchor
              ) {
                marked = key;
              }
              bus.emit(events.READ_UPDATED, { conversationId: id, payload });
            })
            .catch((error) => {
              if (disposed || id !== conversationId || members.state.member?.id !== member.id) {
                return;
              }
              state.error =
                'Não foi possível confirmar a leitura. ' +
                (error.userMessage || 'Tente atualizar o histórico.');
              if (marked === key) {
                marked = null;
              }
            })
            .finally(() => {
              pending.delete(key);
            });
        }
        return {
          state,
          select(id) {
            if (conversationId !== id) {
              conversationId = id;
              visibleId = null;
              state.error = null;
              marked = null;
            }
          },
          visible(command) {
            if (command.conversationId === conversationId) {
              visibleId = command.throughMessageId;
              mark();
            }
          },
          memberChanged() {
            marked = null;
            mark();
          },
          dispose() {
            disposed = true;
            visibleId = null;
          },
        };
      },
    };
  },
]);
