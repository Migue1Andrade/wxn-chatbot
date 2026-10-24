export const AsyncStatus = Object.freeze({
  IDLE: 'IDLE',
  LOADING: 'LOADING',
  SUCCESS: 'SUCCESS',
  ERROR: 'ERROR',
});
export class AsyncState {
  constructor() {
    this.status = AsyncStatus.IDLE;
    this.error = null;
  }
  get loading() {
    return this.status === AsyncStatus.LOADING;
  }
  begin() {
    this.status = AsyncStatus.LOADING;
    this.error = null;
  }
  succeed() {
    this.status = AsyncStatus.SUCCESS;
    this.error = null;
  }
  fail(error) {
    this.status = AsyncStatus.ERROR;
    this.error = error?.userMessage || error?.message || 'Não foi possível concluir a operação.';
  }
  reset() {
    this.status = AsyncStatus.IDLE;
    this.error = null;
  }
}
