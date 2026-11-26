export function requestedTeamPage(currentPage, more) {
  return more ? currentPage + 1 : 0;
}
