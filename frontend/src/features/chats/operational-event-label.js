export function operationalEventLabel(event, memberName) {
  switch (event.event) {
    case 'CONVERSATION_CREATED':
      return 'Conversa iniciada';
    case 'MEMBER_ASSUMED':
      return 'Atendimento assumido por ' + memberName(event.newMemberId);
    case 'MEMBER_ASSIGNED':
      return 'Atendimento atribuído a ' + memberName(event.newMemberId);
    case 'MEMBER_TRANSFERRED':
      return (
        'Transferido de ' + memberName(event.previousMemberId) + ' para ' + memberName(event.newMemberId)
      );
    case 'CONVERSATION_RESOLVED':
      return 'Atendimento resolvido';
    case 'CONVERSATION_PRIORITY_CHANGED':
      return 'Prioridade alterada';
    case 'CONVERSATION_STATUS_CHANGED':
      return 'Status do atendimento alterado';
    case 'CONVERSATION_CLOSED':
      return 'Atendimento encerrado por ' + memberName(event.actorMemberId);
    case 'CONVERSATION_REOPENED':
      return 'Atendimento reaberto por ' + memberName(event.actorMemberId);
    case 'CONVERSATION_AUTO_REOPENED':
      return 'Reaberto automaticamente por nova mensagem';
    default:
      return 'Atendimento atualizado';
  }
}
