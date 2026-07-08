import { ref } from 'vue'

const openRequest = ref(0)

export function useWorkingCountryPicker() {
  return {
    openRequest,
    requestOpenCountryPicker() {
      openRequest.value += 1
    },
  }
}
