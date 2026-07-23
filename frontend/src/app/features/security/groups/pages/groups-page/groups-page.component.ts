import { ChangeDetectionStrategy, Component, OnInit, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { GroupsStore } from '../../data-access/groups.store';
import { toCreateGroupRequest, toUpdateGroupRequest } from '../../domain/group.model';

@Component({
  selector: 'app-groups-page',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule],
  providers: [GroupsStore],
  templateUrl: './groups-page.component.html',
})
export class GroupsPageComponent implements OnInit {
  protected readonly store = inject(GroupsStore);
  private readonly fb = inject(FormBuilder);

  protected readonly createPermissions = new Set<string>();
  protected readonly editPermissions = new Set<string>();

  protected readonly createForm = this.fb.nonNullable.group({
    code: ['', [Validators.required, Validators.maxLength(80)]],
    name: ['', [Validators.required, Validators.maxLength(160)]],
    description: [''],
  });

  protected readonly editForm = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.maxLength(160)]],
    description: [''],
  });

  ngOnInit(): void {
    this.store.load();
  }

  protected toggleCreatePermission(code: string, event: Event): void {
    const checked = (event.target as HTMLInputElement).checked;
    if (checked) {
      this.createPermissions.add(code);
    } else {
      this.createPermissions.delete(code);
    }
  }

  protected toggleEditPermission(code: string, event: Event): void {
    const checked = (event.target as HTMLInputElement).checked;
    if (checked) {
      this.editPermissions.add(code);
    } else {
      this.editPermissions.delete(code);
    }
  }

  protected create(): void {
    if (this.createForm.invalid) {
      return;
    }
    const value = this.createForm.getRawValue();
    this.store.create(
      toCreateGroupRequest(value, this.createPermissions),
      () => {
        this.createForm.reset();
        this.createPermissions.clear();
      },
    );
  }

  protected select(groupId: string): void {
    this.store.select(groupId);
    const group = this.store.groups().find(g => g.id === groupId);
    if (!group || group.systemGroup) {
      return;
    }
    this.editForm.setValue({
      name: group.name,
      description: group.description ?? '',
    });
    this.editPermissions.clear();
    for (const code of group.permissions) {
      this.editPermissions.add(code);
    }
  }

  protected save(): void {
    const group = this.store.selected();
    if (!group || group.systemGroup || this.editForm.invalid) {
      return;
    }
    const value = this.editForm.getRawValue();
    this.store.update(group.id, toUpdateGroupRequest(value, this.editPermissions, group.version));
  }
}
