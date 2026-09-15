# Implementation Plan - Improve Add Expense Animations

Fix the non-smooth animation when opening the Add Expense screen and ensure a smooth closing animation.

## Proposed Changes

### [Navigation]

#### [MODIFY] [AddExpenseNavTransitions.kt](file:///C:/workspace/SplitEase/app/app/src/main/java/com/splitease/app/presentation/navigation/AddExpenseNavTransitions.kt)
- Increase `ADD_EXPENSE_TRANSITION_MS` from 220ms to 400ms.
- Use `EaseInOutCubic` for smoother acceleration/deceleration.
- Update `addExpenseEnterTransition` and `addExpensePopExitTransition` to use these new specs.
- Add `addExpenseSlideEnterTransition` and `addExpenseSlidePopExitTransition` for cases where the screen doesn't expand from the FAB (e.g., when coming from the Friend/Group Picker).

#### [MODIFY] [SplitEaseNavHost.kt](file:///C:/workspace/SplitEase/app/app/src/main/java/com/splitease/app/presentation/navigation/SplitEaseNavHost.kt)
- Apply the new slide transitions to the `ADD_EXPENSE` destination as fallbacks when FAB expansion isn't used.
- Add smooth slide-up/down transitions for the `ADD_EXPENSE_PICKER` destination.

## Verification Plan

### Automated Tests
- Build the project to ensure no syntax errors in the new transition logic.

### Manual Verification
1. Open the app and navigate to a Group or Friend detail.
2. Tap the "Add Expense" FAB and verify it expands smoothly from the button.
3. Tap "Back" or "Cancel" and verify it folds back into the FAB smoothly.
4. From the home screen, tap the main "Add Expense" FAB to open the Picker. Verify it slides up smoothly.
5. Select a friend/group from the Picker. Verify the transition to the Add Expense form is smooth.
6. Complete or cancel the expense and verify the exit animation is smooth.
