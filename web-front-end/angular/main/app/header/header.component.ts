import { Component, OnInit, Output, EventEmitter } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../service/auth.service';

@Component({
  selector: 'app-header',
  templateUrl: './header.component.html',
  styleUrls: ['./header.component.scss']
})
export class HeaderComponent implements OnInit {

  @Output() switchTheme = new EventEmitter();

  constructor(public auth: AuthService, private router: Router) { }

  ngOnInit(): void {
  }

  logout() {
    this.auth.logout();
    this.router.navigate(['/login']);
  }

}
